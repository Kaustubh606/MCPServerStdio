# MCPServerStdio

# Spring AI MCP HelpDesk Server — STDIO

A Spring Boot based **Model Context Protocol (MCP) Server** implemented using **Spring AI** and the **STDIO transport**.

This project demonstrates how an existing Java/Spring Boot backend capability can be exposed to AI applications as MCP tools while keeping the normal enterprise application layers such as Service, Repository and Database intact.

The sample domain is a simple **HelpDesk Ticket Management System** backed by Oracle Database.

---

## Overview

The MCP Server exposes HelpDesk functionality through MCP tools.

Currently supported operations:

- Create a support ticket
- Fetch support tickets for the current user
- Pass trusted user information through MCP metadata
- Persist ticket information using Spring Data JPA and Oracle
- Run the MCP server over STDIO
- Write application logs to a file instead of STDOUT

The MCP server itself does not contain an LLM.

An external MCP Client or AI application connects to this server and decides when to invoke the exposed tools.

---

## Architecture

```text
User
  |
  v
AI Application / MCP Client
  |
  | MCP over STDIO
  v
Spring AI MCP Server
  |
  v
MCP Tool
  |
  v
HelpDeskTicketService
  |
  v
HelpDeskTicketRepository
  |
  v
Oracle Database
```

The application follows the same layered design normally used in a Spring Boot backend.

```text
Traditional REST Application

REST Controller
      |
      v
Service
      |
      v
Repository
      |
      v
Database
```

With MCP:

```text
AI Application
      |
      v
MCP Tool
      |
      v
Service
      |
      v
Repository
      |
      v
Database
```

The MCP Tool therefore acts as an **AI-facing adapter** over the existing backend service layer.

---

## Technology Stack

- Java 25
- Spring Boot 4.0.9
- Spring AI 2.0.1
- Model Context Protocol
- MCP STDIO Transport
- Spring Data JPA
- Oracle Database
- Maven
- Lombok

The project uses Spring Boot `4.0.9`, Java `25` and Spring AI `2.0.1`. 

The MCP server dependency is:

```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-mcp-server</artifactId>
</dependency>
```



---

# MCP Transport — STDIO

This project uses the **STDIO transport**.

```yaml
spring:
  ai:
    mcp:
      server:
        enabled: true
        stdio: true
        name: mcp-server-stdio
        version: 1.0.0
        type: SYNC
```



With STDIO, the MCP Client launches the MCP Server process and communicates with it through:

```text
STDIN
STDOUT
```

Conceptually:

```text
MCP Client
    |
    | starts Java process
    v
java -jar mcpserverstdio.jar
    |
    | STDIN / STDOUT
    v
MCP Server
```

Unlike a normal REST server, this MCP Server does not need to expose an HTTP endpoint.

---

# Non-Web Application

The application is configured as a non-web Spring Boot application:

```yaml
spring:
  main:
    web-application-type: none
    banner-mode: off
```



This is appropriate for STDIO communication because the application communicates through the MCP protocol rather than through REST endpoints.

---

# Important STDIO Logging Configuration

STDIO MCP communication uses standard input and standard output for protocol messages.

For this reason, application output should not pollute STDOUT.

The project disables console logging:

```yaml
logging:
  console:
    enabled: false
```

and writes application logs to:

```yaml
logging:
  file:
    name: ./logs/mcpserverstdio.log
```



Hibernate SQL output is also disabled:

```yaml
spring:
  jpa:
    show-sql: false
```



This is especially important with STDIO-based MCP servers because arbitrary console output can interfere with MCP protocol messages.

---

# Database Configuration

The project uses Oracle XE.

Example configuration:

```yaml
spring:
  datasource:
    url: jdbc:oracle:thin:@//localhost:1521/XEPDB1
    username: ${DB_USER}
    password: ${DB_PASSWORD}
    driver-class-name: oracle.jdbc.OracleDriver
```



Credentials are read from environment variables:

```text
DB_USER
DB_PASSWORD
```

Do not hard-code or commit database credentials to GitHub.

---

# HelpDesk Ticket Entity

Tickets are stored in:

```text
helpdesk_tickets
```

The entity contains:

```text
id
username
issue
status
createdAt
eta
```



Example structure:

```java
@Entity
@Table(name = "helpdesk_tickets")
public class HelpDeskTicket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String username;
    private String issue;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime eta;
}
```

New tickets are initially created with:

```text
status = OPEN
```

and their ETA is currently calculated as:

```text
Current Time + 7 Days
```

This behavior is implemented in the service layer.

---

# Ticket Request

The ticket creation request currently contains the issue description:

```java
public record TicketRequest(String issue) {
}
```



---

# Repository Layer

The repository uses Spring Data JPA:

```java
public interface HelpDeskTicketRepository
        extends JpaRepository<HelpDeskTicket, Long> {

    List<HelpDeskTicket> findByUsername(String username);
}
```



Spring Data automatically derives the query for finding tickets by username.

---

# Service Layer

Business operations remain inside the normal Spring service layer.

```java
@Service
@RequiredArgsConstructor
public class HelpDeskTicketService {
    ...
}
```

The service provides:

```text
createTicket(...)
getTicketByUsername(...)
```



For example, ticket creation is performed by the service rather than directly inside the MCP tool.

```text
MCP Tool
   |
   v
HelpDeskTicketService
   |
   v
HelpDeskTicketRepository
```

This separation keeps MCP-specific integration code away from the actual business logic.

---

# MCP Tools

The server currently exposes two tools:

```text
createTicket
getTicketStatus
```



---

## 1. createTicket

The `createTicket` MCP Tool creates a new support ticket.

```java
@McpTool(
    name = "createTicket",
    description = "Create the support ticket"
)
String createTicket(
        @McpToolParam(
            description = "Details to create a support ticket"
        )
        TicketRequest req,
        McpMeta meta) {

    ...
}
```



Example user request:

```text
My VPN is not working. Create a support ticket.
```

Conceptual flow:

```text
User
  |
  v
LLM
  |
  | decides to call createTicket
  v
MCP Client
  |
  | STDIO
  v
MCP Server
  |
  v
createTicket()
  |
  v
HelpDeskTicketService
  |
  v
Repository
  |
  v
Oracle
```

After saving the ticket, the tool returns a confirmation containing the generated ticket ID and username.

---

## 2. getTicketStatus

The second MCP Tool fetches tickets belonging to the current user.

```java
@McpTool(
    name = "getTicketStatus",
    description = "Fetch the status of the tickets for the current user"
)
List<HelpDeskTicket> getTicketStatus(McpMeta meta) {
    ...
}
```



Example user request:

```text
Show me all my support tickets.
```

Flow:

```text
User
  |
  v
LLM
  |
  | selects getTicketStatus
  v
MCP Client
  |
  v
MCP Server
  |
  v
HelpDeskTicketService
  |
  v
findByUsername(username)
  |
  v
Oracle
```

---

# MCP Metadata

The username is not defined as a normal LLM-generated tool parameter.

Instead, the MCP tools obtain it from MCP metadata:

```java
String username =
        (String) meta.get("username");
```

Both tools use the metadata value to determine the current user. 

Conceptually:

```text
Application Authentication / User Context
                 |
                 v
            MCP Client
                 |
                 | MCP Metadata
                 v
            MCP Server
                 |
                 v
              McpMeta
                 |
                 v
             MCP Tool
```

This is preferable to asking the LLM to generate trusted identity information as an ordinary tool argument.

The LLM decides **which tool to invoke**, while application-owned context such as username can remain outside the model-generated arguments.

---

# Example Tool Flow

Suppose the user asks:

```text
Create a support ticket because my VPN is not connecting.
```

The complete flow becomes:

```text
User
  |
  v
AI Application
  |
  v
LLM identifies createTicket
  |
  v
MCP Client
  |
  | STDIO + metadata
  v
MCP Server
  |
  v
createTicket()
  |
  | TicketRequest
  | username from McpMeta
  v
HelpDeskTicketService
  |
  v
HelpDeskTicketRepository
  |
  v
Oracle Database
```

---

# Building the Project

Clone the repository and move into the project directory.

Configure the required environment variables:

```text
DB_USER=<oracle-username>
DB_PASSWORD=<oracle-password>
```

Build using Maven:

```bash
mvn clean package
```

The Spring Boot executable JAR will be generated under:

```text
target/
```

---

# Running the STDIO Server

The generated JAR can be launched using:

```bash
java -jar target/mcpserverstdio-0.0.1-SNAPSHOT.jar
```

The artifact name is defined as:

```xml
<artifactId>mcpserverstdio</artifactId>
<version>0.0.1-SNAPSHOT</version>
```



For normal MCP usage, however, the MCP Client generally launches this process and communicates with it over STDIO.

---

# Example MCP Client / Inspector Configuration

A typical STDIO client configuration would provide:

```text
Command:
java
```

Arguments:

```text
-jar
/path/to/mcpserverstdio-0.0.1-SNAPSHOT.jar
```

Environment variables:

```text
DB_USER=<oracle-username>
DB_PASSWORD=<oracle-password>
```

The important point is that `-jar` and the JAR path are separate command-line arguments.

---

# Project Structure

```text
src/main/java/com/aiwork/mcpserverstdio
│
├── entity
│   └── HelpDeskTicket.java
│
├── model
│   └── TicketRequest.java
│
├── repository
│   └── HelpDeskTicketRepository.java
│
├── service
│   └── HelpDeskTicketService.java
│
└── tools
    └── HelpDeskTools.java
```

The responsibilities are deliberately separated:

```text
HelpDeskTools
    ↓
MCP integration layer

HelpDeskTicketService
    ↓
Business/service layer

HelpDeskTicketRepository
    ↓
Persistence layer

HelpDeskTicket
    ↓
Database entity
```

---

# Why Use MCP Here?

Without MCP, an AI application could require a custom integration for every backend capability.

```text
AI Application
      |
      +---- Custom HelpDesk integration
      +---- Custom File integration
      +---- Custom GitHub integration
      +---- Custom Database integration
```

MCP provides a standardized mechanism through which servers can expose capabilities to MCP-compatible clients.

For this project:

```text
AI Application
      |
      v
MCP Client
      |
      | STDIO
      v
HelpDesk MCP Server
      |
      v
Existing Spring Backend
```

The existing Java business layer does not need to be replaced by AI-specific logic.

---

# Key Design Principle

**The LLM should not contain enterprise business logic.**

The LLM can decide which capability is required, while actual application behavior remains inside deterministic Java code.

```text
LLM
 |
 | chooses tool
 v
MCP Tool
 |
 | delegates
 v
Service
 |
 | business logic
 v
Repository
 |
 v
Database
```

This preserves familiar Spring Boot architectural principles while adding AI integration through MCP.

---

# Security Notes

Never commit:

```text
Database passwords
API keys
Access tokens
Production credentials
Customer data
Company confidential information
```

Use environment variables or an enterprise secrets-management solution for credentials.

The current project already reads Oracle credentials from:

```text
${DB_USER}
${DB_PASSWORD}
```



---

# Current Features

This project currently demonstrates:

- Spring AI MCP Server
- STDIO MCP transport
- Synchronous MCP server
- Annotation-based MCP tools
- `@McpTool`
- `@McpToolParam`
- MCP metadata using `McpMeta`
- Trusted username propagation
- HelpDesk ticket creation
- Ticket retrieval by username
- Spring Data JPA
- Oracle XE integration
- Layered Service/Repository architecture
- File-based logging suitable for STDIO MCP communication

---

# Learning Objective

The purpose of this repository is to demonstrate how an existing **Java/Spring Boot enterprise backend** can expose its capabilities to AI applications using the **Model Context Protocol**.

The main architectural idea is:

```text
AI / LLM
   |
   v
MCP
   |
   v
Existing Java Backend
```

rather than moving application business logic into the LLM.

This allows AI capabilities to be added while retaining familiar enterprise patterns such as:

```text
Service Layer
Repository Layer
JPA
Database
Externalized Configuration
Logging
```

---

## Future Enhancements

Possible extensions to this learning project include:

- Additional HelpDesk MCP tools
- Authentication and authorization
- Tool-level access control
- Input validation
- MCP Resources
- MCP Prompts
- Additional observability
- Automated tests
- Containerized database setup

---

## Disclaimer

This repository is a learning and demonstration project intended to explore **Spring AI and Model Context Protocol integration with Java/Spring Boot applications**.
