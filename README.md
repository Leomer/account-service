# account-service

Bank account management microservice. Spring Boot, WebFlux, RxJava 3, MongoDB.

## Requirements
- JDK 17, Maven, MongoDB
- config-server running on :8888
- customer-service running on :8081 (used to validate customers)

## Run
1. Start MongoDB, config-server and customer-service
2. `mvn spring-boot:run`
- Port: 8082 (from config-repo)
- Database: `account_db`
- Swagger UI: http://localhost:8082/swagger-ui.html

## Business rules
| Customer | Rule |
|---|---|
| PERSONAL | Max. 1 savings account and 1 checking account; unlimited fixed-term accounts |
| BUSINESS | No savings or fixed-term accounts; unlimited checking accounts |
| BUSINESS | At least one holder; zero or more authorized signers |

Default values by account type:
| Account | Maintenance fee | Monthly movement limit |
|---|---|---|
| SAVINGS | 0 | configurable (`business.savings-movement-limit`) |
| CHECKING | configurable (`business.checking-maintenance-fee`) | unlimited |
| FIXED_TERM | 0 | 1 |

## Endpoints
| Method | Path | Description |
|---|---|---|
| POST | /accounts | Create an account |
| GET | /accounts | List all accounts |
| GET | /accounts/{accountNumber} | Find by account number |
| GET | /accounts/customer/{customerDocument} | List accounts of a customer |
| PUT | /accounts/{accountNumber} | Update holders, signers, fee and limit |
| PATCH | /accounts/{accountNumber}/balance | Atomic balance change (used by transaction-service) |
| DELETE | /accounts/{accountNumber} | Delete an account |

## Design patterns
| Pattern | Where | Purpose |
|---|---|---|
| Strategy | `strategy/` (Savings, Checking, FixedTerm) | Rules, defaults and limits per account type without if/switch chains |
| Factory | `factory/AccountFactory` | Single place that builds a new account (number, defaults, profile) |
| Builder | Lombok `@Builder` on models and DTOs | Readable object construction |
| Mapper | `mapper/` | Entity to DTO conversion |
| Registry | `AccountStrategyRegistry` | Finds a strategy by account type |

## Documentation
See the `docs/` folder: OpenAPI contract and sequence diagram.
