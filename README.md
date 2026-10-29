This program runs in 2 instances. Run and compile the first with the command:

mvn clean compile exec:java -Dexec.mainClass="com.orchestrator.Orchestrator" -Dexec.args="input"

Then, run and compile the second with the command:

mvn clean compile exec:java -Dexec.mainClass="com.orchestrator.Orchestrator" -Dexec.args="<AWS_ACCESS_KEY_ID> <AWS_SECRET_ACCESS_KEY> <IAM_INSTANCE_PROFILE_NAME> <TAILSCALE_AUTH_KEY> <ORCHESTRATOR_TAILSCALE_IP>"

To run this program successfully, you will need to have a reusable authkey from TailScale and an access key id, a secret access key, and an IAM instance profile name with the roles: 

1. AmazonSSMManagedInstanceCore
2. AmazonS3ReadOnlyAccess
