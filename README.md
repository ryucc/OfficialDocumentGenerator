# Official Document Generator

A Spring Boot API for managing document-writing instructions.

## Technology Stack

- **Java 17**
- **Spring Boot 3.2.3**
- **Spring Data JPA**
- **H2 Database** (development)
- **PostgreSQL** (production)
- **Maven**
- **SpringDoc OpenAPI** (API documentation)
- **Lombok**

## API Specification

OpenAPI specification is located at `src/main/resources/openapi/openapi.yaml`

## Project Structure

```
src/
├── main/
│   ├── java/com/officialpapers/api/
│   │   ├── config/          # Configuration classes
│   │   ├── controller/      # REST controllers
│   │   ├── dto/             # Data Transfer Objects
│   │   ├── entity/          # JPA entities
│   │   ├── enums/           # Enumerations
│   │   ├── mapper/          # Entity-DTO mappers
│   │   ├── repository/      # Spring Data repositories
│   │   ├── service/         # Business logic services
│   │   └── OfficialPaperGptApplication.java
│   └── resources/
│       ├── application.properties
│       └── application-prod.properties
└── test/
    └── java/com/officialpapers/api/
```

## Getting Started

### Prerequisites

- Java 17 or higher
- Maven 3.6+

### Running the Application

1. **Clone the repository**

2. **Build the project**
   ```bash
   mvn clean install
   ```

3. **Run the application**
   ```bash
   mvn spring-boot:run
   ```

The application will start on `http://localhost:8080`

### API Documentation

Once the application is running, access the Swagger UI at:
- **Swagger UI**: http://localhost:8080/swagger-ui.html
- **OpenAPI JSON**: http://localhost:8080/v3/api-docs

### H2 Console (Development)

Access the H2 database console at:
- **URL**: http://localhost:8080/h2-console
- **JDBC URL**: `jdbc:h2:mem:officialpaperdb`
- **Username**: `sa`
- **Password**: (leave blank)

## API Endpoints

### Document Instructions
- `GET /api/v1/document-instructions` - List document-writing instructions
- `POST /api/v1/document-instructions` - Create a document-writing instruction
- `GET /api/v1/document-instructions/{instructionId}` - Get a document-writing instruction
- `PUT /api/v1/document-instructions/{instructionId}` - Update a document-writing instruction
- `DELETE /api/v1/document-instructions/{instructionId}` - Delete a document-writing instruction

## Configuration

All configuration is in `src/main/resources/application.yaml`.

### Development Profile (default)
Uses H2 in-memory database.

### Production Profile
Uses PostgreSQL database. Run with:
```bash
mvn spring-boot:run -Dspring-boot.run.profiles=prod
```

### Environment Variables (Production)
- `DATABASE_URL` - PostgreSQL connection URL
- `DATABASE_USERNAME` - Database username
- `DATABASE_PASSWORD` - Database password

## Building for Production

Create an executable JAR:
```bash
mvn clean package
```

Run the JAR:
```bash
java -jar target/official-paper-gpt-0.1.0.jar --spring.profiles.active=prod
```

## Development Notes

- The application uses JWT-based authentication (placeholder - needs implementation)
- Document instruction management provides CRUD operations for storing and retrieving document templates

## License

This project is part of a university assignment.
