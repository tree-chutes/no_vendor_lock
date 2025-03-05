# Lockless cloud native micro services

Note that less one hundred lines of code are required in both consumer files. Also note that no references to vendor sdks are present. This means that the domain/business logic can execute anywhere.

Note the driver file (Java) does not have references to vendor sdks nor cloudops technology. It is a simple set of rest endpoints that can be invoked from any middleware layer. 

