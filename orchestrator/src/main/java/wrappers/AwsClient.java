package wrappers;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.*;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.*;
import software.amazon.awssdk.core.sync.RequestBody;

import java.io.File;
import java.util.Collections;
import java.util.List;

public class AwsClient {

    private final Ec2Client ec2Client;
    private final S3Client s3Client;
    private final SsmClient ssmClient;

    private AwsClient(String accessKey, String secretKey) {
        StaticCredentialsProvider provider = StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey));

        this.ec2Client = Ec2Client.builder()
                .region(software.amazon.awssdk.regions.Region.of("ap-southeast-5"))
                .credentialsProvider(provider)
                .build();

        this.s3Client = S3Client.builder()
                .region(software.amazon.awssdk.regions.Region.of("ap-southeast-5"))
                .credentialsProvider(provider)
                .build();

        this.ssmClient = SsmClient.builder()
                .region(software.amazon.awssdk.regions.Region.of("ap-southeast-5"))
                .credentialsProvider(provider)
                .build();
    }

    public static AwsClient login(String accessKey, String secretKey) {
        return new AwsClient(accessKey, secretKey);
    }

    public String createEC2(String ami, String instanceType) {
        return ec2Client.runInstances(
                RunInstancesRequest.builder()
                        .imageId(
                                ssmClient.getParameter(
                                        GetParameterRequest.builder()
                                                .name(ami)
                                                .build()
                                ).parameter().value()
                        )
                        .instanceType(instanceType)
                        .minCount(1)
                        .maxCount(1)
                        .build()
        ).instances().get(0).instanceId();
    }

    public void associateEc2WithIaM(String instanceId, String instanceProfileName) {
        ec2Client.associateIamInstanceProfile(
                AssociateIamInstanceProfileRequest.builder().instanceId(instanceId).iamInstanceProfile(
                        IamInstanceProfileSpecification.builder().name(instanceProfileName).build()
                ).build()
        );
    }

    public String createS3(String bucketName) {
        s3Client.createBucket(
                CreateBucketRequest.builder()
                        .bucket(bucketName)
                        .createBucketConfiguration(
                                CreateBucketConfiguration.builder()
                                        .locationConstraint("ap-southeast-5")
                                        .build()
                        )
                        .build()
        );
        return bucketName;
    }

    public void uploadToS3(String bucketName, String fileName) {
        s3Client.putObject(PutObjectRequest.builder().bucket(bucketName).key(fileName).build(), RequestBody.fromFile(new File(fileName)));
    }

    public void ssmToEc2(String instanceId, String command, boolean wait) {
        SendCommandResponse response = ssmClient.sendCommand(
                SendCommandRequest.builder()
                        .instanceIds(Collections.singletonList(instanceId))
                        .documentName("AWS-RunShellScript")
                        .parameters(Collections.singletonMap("commands", List.of(command)))
                        .build()
        );

        if (wait) {
            while (true) {
                try {
                    CommandInvocationStatus status = ssmClient.getCommandInvocation(
                            GetCommandInvocationRequest.builder()
                                    .commandId(response.command().commandId())
                                    .instanceId(instanceId)
                                    .build()
                    ).status();

                    if (status == CommandInvocationStatus.SUCCESS
                            || status == CommandInvocationStatus.FAILED
                            || status == CommandInvocationStatus.CANCELLED
                            || status == CommandInvocationStatus.TIMED_OUT)
                        break;
                } catch (InvocationDoesNotExistException e) {}

                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {}
            }
        }
    }

    public void terminateEc2(String instanceId) {
        ec2Client.terminateInstances(TerminateInstancesRequest.builder().instanceIds(instanceId).build());
    }

    public void deleteS3(String bucketName) {
        for (
                S3Object object : s3Client.listObjectsV2(
                        ListObjectsV2Request.builder()
                                .bucket(bucketName)
                                .build()
                ).contents()
            ) {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(object.key())
                    .build());
        }

        s3Client.deleteBucket(DeleteBucketRequest.builder()
                .bucket(bucketName)
                .build());
    }

    public boolean checkEC2InitializationStatus(String instanceId) {
        DescribeInstanceStatusResponse response = ec2Client.describeInstanceStatus(
                DescribeInstanceStatusRequest.builder()
                        .instanceIds(instanceId)
                        .build()
        );

        if (response.instanceStatuses().isEmpty())
            return false;

        return response.instanceStatuses().get(0).instanceState().nameAsString().equals("running");
    }

    public boolean checkSSMCheckInStatus(String instanceId) {
        return !ssmClient.describeInstanceInformation(
                DescribeInstanceInformationRequest.builder()
                        .filters(InstanceInformationStringFilter.builder()
                                .key("InstanceIds")
                                .values(instanceId)
                                .build())
                        .build()
        ).instanceInformationList().isEmpty();
    }

    public String getPrivateIp(String instanceId) {
        return ec2Client.describeInstances(
                DescribeInstancesRequest.builder()
                        .instanceIds(instanceId)
                        .build()
        ).reservations().get(0).instances().get(0).privateIpAddress();
    }

    public boolean checkInstanceTerminationStatus(String instanceId) {
        return c2Client.describeInstances(
                DescribeInstancesRequest.builder()
                        .instanceIds(instanceId)
                        .build()
        ).reservations().get(0).instances().get(0)
                .state().name() == InstanceStateName.TERMINATED;
    }
}