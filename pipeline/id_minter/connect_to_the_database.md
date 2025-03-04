# Connecting to the ID minter database

Occasionally it's useful to connect to the ID minter database to correct a record, but we do it very sparingly.

The database is located in a private subnet and cannot be accessed from the outside world. However, it is possible
to connect to the instance via an existing bastion host following these steps:

1. In the RDS console, create a [manual snapshot](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/USER_CreateSnapshot.html) of the ID minter database before you start.

   Depending on what you're doing, you may also want to turn off any running ID minter tasks to prevent interference.

2. In the AWS platform account, locate an EC2 instance with the `id-minter-bastion` Instance ID (see [here](https://eu-west-1.console.aws.amazon.com/ec2/home?region=eu-west-1#Instances:search=:id-minter-bastion))
   and start it. (Make sure to stop the instance when you're done!)

3. Locate the security group attached to the EC2 instance (named `id-minter-bastion`) and add a new inbound rule 
   to allow SSH traffic from your IP address.

4. Locate the `ssh/wellcomedigitalplatform` secret in Secrets Manager and save the secret value to your local machine
   as a `.pem` file.

5. Locate and note down the Public IPv4 DNS value of the EC2 instance.

6. Locate and note down the endpoint name of the id minter database in RDS.

7. Create an SSH tunnel between your local machine and the database via the following command, replacing placeholders
   with values retrieved in previous steps:

   ```sh
   ssh -i "<path to .pem file>" -f -N -L 3306:<database cluster endpoint name>:3306 ec2-user@<bastion host public DNS> -v
   ```

8. Install MySQL and connect to the RDS instance via localhost:

   ```sh
   brew install mysql@8.4
   mysql -h 127.0.0.1 -P 3306 -u wellcome -p
   ```

9. Stop the bastion instance when you are done and consider removing your IP address from the inbound rules of
   the `id-minter-bastion` security group.

## Modifying the ID minter database

Here are some useful commands:

```mysql
USE identifiers;

SELECT *
FROM identifiers
WHERE CanonicalId = 'w62ubcaw';

UPDATE identifiers
SET SourceId = 'b20442506/FILE_0518_OBJECTS'
WHERE SourceId = 'B20442506/FILE_0518_OBJECTS'
  AND OntologyType = 'Image'
  AND SourceSystem = 'mets-image';

DEL/ETE FROM identifiers WHERE SourceSystem = 'made-up-example';
```
