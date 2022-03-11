# Connecting to the ID minter database

Occasionally it's useful to connect to the ID minter database to correct a record, but we do it very sparingly.

You can't connect to the ID minter directly from the outside world – you have to use a bastion host.

These are some loose notes I wrote the last time I had to do it; there may be gaps in these instructions because I didn't have time to test them fully, but hopefully they're a useful guide.

1.  Launch an EC2 instance which can connect to the ID minter database (the "bastion instance").

    This means launching the smallest EC2 instance you can get, with the following settings:

    -   In the catalogue VPC
    -   In a private subnet
    -   In the `id_minter_bastion_ssh_access`, `id_minter_instance_ssh_access` and `identifiers_database_sg` security groups
    -   An SSH key that you have access to

2.  Launch an EC2 instance which you can connect to (the "externally visible instance").

    This means launching the smallest EC2 instance you can get, with the following settings:

    -   In the catalogue VPC
    -   In a public subnet and public IP
    -   The same security groups as the previous step
    -   An SSH key that you have access to

3.  Update the security group `id_minter_instance_ssh_access` to allow traffic on port 22 from your local IP address.

    Update the security group `identifiers_database_sg` to allow traffic on port 22 from your private instance.

4.  Connect to the externally visible host using your SSH key.
    Upload your SSH key to this host, e.g.

    ```console
    $ scp -i ~/.ssh/wellcomedigitalplatform ec2-user@ec2-54-229-72-34.eu-west-1.compute.amazonaws.com:~/.ssh/wellcomedigitalplatform
    $ ssh -i ~/.ssh/wellcomedigitalplatform ec2-user@ec2-54-229-72-34.eu-west-1.compute.amazonaws.com
    ```

5.  Connect to the bastion host using your SSH key, e.g.

    ```console
    $ ssh -i ~/.ssh/wellcomedigitalplatform ec2-user@ip-172-31-180-161.eu-west-1.compute.internal
    ```

6.  Install MySQL and connect to the RDS instance:

    ```console
    $ sudo yum install -y mysql
    $ mysql --host=$HOST --port=3306 --user=$USERNAME --password=$PASSWORD
    ```

    You can get the host/usernamepassword values from the RDS and Secrets Manager consoles, respectively.

## Modifying the ID minter database

Here are some useful commands:

```mysql
USE identifiers;

SELECT * FROM identifiers WHERE CanonicalId = 'w62ubcaw';

UPDATE identifiers SET SourceId = 'b20442506/FILE_0518_OBJECTS'
WHERE SourceId = 'B20442506/FILE_0518_OBJECTS'
AND OntologyType = 'Image'
AND SourceSystem = 'mets-image';
```
