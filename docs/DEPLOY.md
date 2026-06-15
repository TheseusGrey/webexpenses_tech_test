# Deploying the Expense Claim System

Currently the app uses a compose file and everything exists as part of a single application. In production you'd probably want something closer to the following:

![Rough diagram for a simple archtecture of the expense claim service](./rough_archtecture_diagram.svg)

A quick walkthrough:

1. **Separate out the authentication logic/flow into a separate service**
    - The goal is to minimise the attack surface. This can also include having a separate RDS instance for holding user account data (either a separate instance, or lock the user table to be accesible by the auth service only).
    - This means if a service _is_ compromised, there's extra work for the attacker to pivot over to where account data is stored.
    - You can also use scope to restrict what other services can obtain from the user data using scope (I.e. the expense claim service doesn't care about employee's home address if that information is stored).
2. **Putting together a Terraform config for running on AWS**
    - `RDS` instance, rather an using postgres container in ec2.
    - `EC2` instance for the service(s) (My go to is 3 replicas for any service in case of failures, though having this scale with traffic is also key).
    - `API Gateway` for protecting internal services, only expose what's required for clients to interact with the system.
    - Some kind of logging/monitoring tools (`elastisearch` + `grafana`).
    - If (and only if) the server takes a while to come back with responses (such that it's causing a bottleneck for performance). Adding a redis cache can aleviate that.
    - You'd probably want a way to store receipts (PDFs, images, etc.) along with claims, something like an S3 bucket would work well here.

Some other thoughts:

- Keeping individual services stateless as much as possible gives more options in terms of scalability (since you can scale horizontally with no issues).
- Integrating with SSO providers through the auth service is another way to reduce security burdens since it minimises the account data you need to store yourself.
- Databases can be frequent sources of bottlenecks when it comes to performance. caching, transactions can help mitigate this.
    