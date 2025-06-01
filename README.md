# Lockless cloud native micro services

Note that less one hundred lines of code are required in both consumer files. Also note that no references to vendor sdks are present. This means that the domain/business logic can execute anywhere. 

In fact, neither Java nor Rust service consumers logic need to use Spring, Micronaut, Actix, Rocket, or Tokio. If one does not use it, one doesn't have to pay for these. 

Note the driver files (Java or Rust) does not have references to vendor sdks nor cloudops technology. It is a simple set of rest endpoints that can be invoked from any middleware layer.

