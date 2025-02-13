# Capacity estimate 

## Storage estimate

The graph will have a node for each work and for each concept. Our intention is to store _all_ LoC and MeSH concepts in the graph
(even though most of them are not referenced by any works) as this will make updating the graph with new edges much easier.

**Number of nodes**: ~20 million (~14 million concepts + ~3 million works + headroom for other nodes)\
**Storage per node**: ~2 KB (assuming we store 10 to 20 fields per node)\
**Number of edges**: ~40 million (the graph will be quite sparse — some nodes will have many edges, but most will have 0 edges)\
**Storage per edge**: ~100 B (there will only be a few fields associated with each edge)\
=====\
**Total storage**: 20 M * 2 K + 40 M + 0.1 K = 44 GB ≈ **<ins>50 GB of storage</ins>**

## Memory & compute estimate

We estimate that the database will receive up to **<ins>10 million requests</ins>** per month (~300,000 per day). On most days it will not receive this many requests, but during a reindex it will receive many more than this. 

Memory consumption depends on several factors (such as the complexity of the queries and how optimised they are) and is difficult to estimate. As an educated guess, we estimate that under heavy load, the database might require up to **<ins>20 GB of memory</ins>**. We also anticipate that most of the time (perhaps 80% of the time) the database will be more or less idle and only require a small amount of memory. 

# Cost estimate

## Neptune serverless 

Neptune serverless automatically scales with usage. Unlike other serverless services, Neptune serverless does not scale to zero — the minimum provisioned capacity is 1 Neptune Capacity Unit (NCU). This means that a completely idle database still incurs costs of **~115 USD per month** plus storage.

According to AWS, 1 NCU corresponds to approximately 2 GB of memory, so we would need 10 NCUs to provide 20 GB of memory.

Using the capacity estimates from above, including the assumption that the database will be under heavy workloads 20% of the time, the monthly cost breakdown would be as follows:

**Idle cost**: 1 NCU * 19.2 hours * 0.16 USD * 30 days ≈ 93 USD\
**Scaled-up cost**: 10 NCUs * 4.8 hours * 0.16 USD * 30 days ≈ 231 USD\
**Request cost**: 10 million * 0.0000002 = 2 USD\
**Storage cost**: 50 GB * 0.1 USD = 5 USD\
=====\
**Total cost per month**: 93 + 231 + 2 + 5 = **<ins>331 USD</ins>**

This calculation assumes we will only be running a single serverless instance. For high availability, we would need to provision read-only replicas across several availability zones, which would incur additional costs. 

## Neptune cluster

AWS offers many instance types to choose from. Some relevant general-purpose instances and their associated costs are included in the table below.

| Instance type                        | Cost per month |
|--------------------------------------|----------------|
| db.r6g.large (16 GB memory, 2 CPU)   | ~240 USD       |
| db.r6g.xlarge (32 GB memory, 4 CPU)  | ~480 USD       |
| db.r6g.2xlarge (64 GB memory, 8 CPU) | ~960 USD       |

Based on the capacity calculations above, the instance type which is most likely to fit our use case costs ~480 USD per month. Request and storage costs are the same as in the serverless calculation above.

**Instance cost**: ~480 USD\
**Request cost**: 10 million * 0.0000002 = 2 USD\
**Storage cost**: 50 GB * 0.1 USD = 5 USD\
=====\
**Total cost per month**: 480 + 2 + 5 = **<ins>487 USD</ins>**

This calculation assumes we will only be running a single instance. For high availability, we would need to provision read-only replicas across several availability zones, which would incur additional costs.

## Neo4j fully managed (AuraDB)

Neo4j offers a fully managed database (called AuraDB) with fixed monthly costs based on plan and configuration. There are
two paid plans — _Professional_ and _Business Critical_. The Business Critical plan offers high availability (with a 99.95% SLA) and premium 24x7 support. The Professional plan makes no availability guarantees.

See table below for relevant configurations and their associated costs. 

| Configuration                      | Cost per month (Professional plan) | Cost per month (Business critical plan) |
|------------------------------------|------------------------------------|-----------------------------------------|
| 16 GB memory, 3 CPU, 32 GB storage | ~1,051 USD                         | 2,336 USD                               |
| 24 GB memory, 5 CPU, 48 GB storage | ~1,577 USD                         | -                                       |
| 32 GB memory, 6 CPU, 64 GB storage | ~2,102 USD                         | 4,672 USD                               |

Based on the capacity calculations above, the configuration which is most likely to fit our use case costs **<ins>1,577 USD per month</ins>**. Additionally, it is unclear whether a fully managed AuraDB hosted in AWS can be easily integrated with our VPC to prevent data egress charges.

Unlike Neptune, storage and compute scale together — if we needed more storage, we would need to pay for more memory and CPUs too. 

## Neo4j self-managed

Neo4j offers two self-managed options — **Community Edition** and **Enterprise Edition**. They do not publicly disclose Enterprise Edition pricing, so 
this calculation assumes we will use the Community Edition, which is free (but comes with limited features).

We can host the database on an EC2 instance. One suitable instance type is *r6g.xlarge* (which is the EC2 equivalent of the *db.r6g.xlarge* type chosen in the Neptune cluster section above).

With this instance, the cost breakdown would be as follows:\
**Instance cost (on-demand)**: ~147 USD\
**Storage costs (gp3 SSD)**: ~50 USD (highly dependent on throughput, IOPS, and snapshot frequency)\
=====\
**Total cost per month**: 147 + 50 = **<ins>197 USD</ins>**

# Conclusion

Based on the calculations above, hosting a self-managed Neo4j database in an EC2 instance comes with the lowest infrastructure costs (at approximately 200 USD per month).
However, this option comes with significant operational overhead in the form of software updates and security patches of the underlying operating system. Additionally,
scaling the database (either vertically by migrating to a larger instance, or horizontally by creating read replicas) would be cumbersome and time-consuming.

Taking operational overhead into account, Neptune serverless is most likely a better fit. It is by far the most flexible option, incurring minimal costs when not in use and automatically scaling up to meet spiky workloads.
Additionally, its estimated costs are only about 50% more (~100 USD per month) than the self-managed option.