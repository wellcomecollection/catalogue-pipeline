
# import socket
# import ssl
# 
# hostname = "catalogue-graph-dev.cluster-cydsvlj5npwq.eu-west-1.neptune.amazonaws.com"
# ctx = ssl.create_default_context()
# ctx.check_hostname = False
# ctx.verify_mode = ssl.CERT_NONE
# with socket.create_connection((hostname, 8182)) as sock:
#     with ctx.wrap_socket(sock, server_hostname=hostname) as ssock:
#         print(ssock.version())
# 
# import boto3
# 
# print(ssl.OPENSSL_VERSION)

from clients.neptune_client import NeptuneClient

c = NeptuneClient("dev")
r = c.run_open_cypher_query("MATCH (c) RETURN COUNT(c)")

print(r)
