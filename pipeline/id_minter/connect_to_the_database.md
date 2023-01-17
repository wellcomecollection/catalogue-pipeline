# Connecting to the ID minter database

Occasionally it's useful to connect to the ID minter database to correct a record, but we do it very sparingly.

You can't connect to the ID minter directly from the outside world – you have to use a bastion host.

These are some loose notes I wrote the last time I had to do it; there may be gaps in these instructions because I didn't have time to test them fully, but hopefully they're a useful guide.

1.  In the RDS console, create a [manual snapshot](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/USER_CreateSnapshot.html) of the ID minter database before you start.

    Depending on what you're doing, you may also want to turn off any running ID minter tasks to prevent interference.

2.  Launch an EC2 instance which can connect to the ID minter database (the "bastion instance").

    This means launching the smallest EC2 instance you can get, with the following settings:

    -   In the catalogue VPC
    -   In a private subnet
    -   In the `id_minter_bastion_ssh_access`, `id_minter_instance_ssh_access` and `identifiers_database_sg` security groups
    -   An SSH key that you have access to

3.  Launch an EC2 instance which you can connect to (the "externally visible instance").

    This means launching the smallest EC2 instance you can get, with the following settings:

    -   In the catalogue VPC
    -   In a public subnet and public IP
    -   The same security groups as the previous step
    -   An SSH key that you have access to

4.  Update the security group `id_minter_instance_ssh_access` to allow traffic on port 22 from your local IP address.

    Update the security group `identifiers_database_sg` to allow traffic on Aurora/MySQL from your bastion instance.
    
    In both cases, label the new inbound rules with your name/date so they can be identified later.

5.  Connect to the externally visible host using your SSH key.
    Upload your SSH key to this host:

    ```console
    $ scp -i <LOCAL_SSH_KEY> <LOCAL_SSH_KEY> ec2-user@<EXTERNALLY_VISIBLE_INSTANCE_DNS>:id_rsa
    ```
    
6.  Connect to the externally visible instance using your SSH key:
    
    ```
    $ ssh -i <LOCAL_SSH_KEY ec2-user@<EXTERNALLY_VISIBLE_INSTANCE_DNS>
    ```

7.  From the externally visible instance, connect to the bastion host using your SSH key:

    ```console
    $ ssh -i id_rsa ec2-user@<BASTION_INSTANCE_DNS>
    ```

8.  Install MySQL and connect to the RDS instance:

    ```console
    $ sudo yum install -y mysql
    $ mysql --host=$HOST --port=3306 --user=$USERNAME --password=$PASSWORD
    ```

    You can get the host/username/password values from the RDS and Secrets Manager consoles, respectively.
    
    If this connection hangs, check the rules you added to `identifiers_database_sg` in the previous step.

## Modifying the ID minter database

Here are some useful commands:

```mysql
USE identifiers;

SELECT * FROM identifiers WHERE CanonicalId = 'w62ubcaw';

UPDATE identifiers SET SourceId = 'b20442506/FILE_0518_OBJECTS'
WHERE SourceId = 'B20442506/FILE_0518_OBJECTS'
AND OntologyType = 'Image'
AND SourceSystem = 'mets-image';

DEL/ETE FROM identifiers WHERE SourceSystem = 'made-up-example';
```
