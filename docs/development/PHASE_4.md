# Development Phase #4 - Risk Assessment Features

## Project Design

### Assumptions

1. The Web Application doesn't manage transactions and users. These entities are read-only.
2. The AI Risk Assessment is triggered on a single transaction, and it uses only the Risk Rules as knowledge source for
   RAG (Retrieval-Augmented Generation).
6. Database is provided as part of the local environment and populated with test data for per purpose of the demo.
7. AI calls are simulated with wiremock, through a stub replaying recorded sessions, to be demoed offline.

### Functional Requirements

| Functionality                  | Description                                                                                                                                                                                                                                                                                                                                                                                                                                                         |
|--------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| AI Risk Assessment             | It should be triggered via a button next to a transaction and display a top level card with live progress, from the Server-Sent Events stream of the stages (e.g. PROMPT_BUILDING, RULE_RETRIEVAL, HISTORY_RETRIEVAL, MODEL_CALL, COMPLETE, FAILED), and the final assessment content: risk level, findings summary, and a set of recommendations..                                                                                                                 |
| AI Risk Assessment Computation | A system prompt will define the guidelines and expecte outputs for the operation. An user prompt will inject the relevant context, such as transaction details relevant to the assessment, hiding and omitting any PII (Personally Identifieable Information), and risk rules and risk assessment history are used as knowledge source for RAG. The triggered rule is used to compute the risk level score weightening the match-strength and the risk rule weight. |
| AI Risk Assessment History     | AI Risk Assessments should be persistent and made avialble to the operator and the assessment process as knowledge sources for the RAG.                                                                                                                                                                                                                                                                                                                             |
| AI Interactions Stubbing       | The AI Risk Assessment should have a feature flag for development, allowing to record the session in order to be used to generate wiremock stubs for offline demo.                                                                                                                                                                                                                                                                                                  |

### Non-Functional Requirements

| Requirement                   | Description                                                                                                                                                                                                                                                                                 |
|-------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Security & Data Protection    | PII, sentitive information, configuration, credentials and any details that could be used by an attacker not be disclosed to AI.                                                                                                                                                            |
| Reliability & Resilience      | SSE should be limited and clean up resources after a timeout. When an operator disconnects during an AI Risk Assessment the assessment should continue and persisted, even if the SSE terminates. SSE Timeout and AI Assessment timeouts should be consistent.                              |
| Observability                 | Each assessment run is traceable end-to-end (which prompt version, which model, which provider, which rules, which RAG sources were retrieved) — useful both for debugging and for regulation.                                                                                              |
| Configurability               | AI provider, model name, and prompt templates are externalized as configuration/code artifacts, not hardcoded.                                                                                                                                                                              |
| Maintainability & Testability | Each module (backend REST layer, RAG/AI service, Angular frontend) should have a clear boundaries, follow clean architecture and clean code principles but prioritize simplifity and avoid unnecessary abstractions, and ensure testing of all features but not of details and boilerplate. |
| Usability                     | Pagination, filters, dropdown and any other frontend element the user interacts with must behave consistently across any component.                                                                                                                                                         |

### High-level APIs

The nature of this web application is suitable to RESTful APIs, for the communication between frontend and backed. The
AI Assessment service will require a Server-Sent Events (SSE) stream to provide feedback about the assessment progress,
since it may take seconds.

**Base Path:** `/api/v1`

| Method  | Endpoint Path                                   | Description                                                                                                                    | Access Level | Request Query / Body                                                                            | Response Payload                                                           |
|---------|-------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------|--------------|-------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------|
| **GET** | `/customers/{customerId}/ai-assessments/stream` | Opens an SSE stream returning typed `AiRiskAssessmentEventDto` JSON objects representing progress tokens and the final summary | Operator     | `?transactionId={uuid}`                                                                         | `200 OK`: `text/event-stream`<br/>Data payload: `AiRiskAssessmentEventDto` |
| **GET** | `/customers/{customerId}/ai-assessments`        | Retrieves paginated history of past persisted AI risk assessments for the customer                                             | Operator     | `?transactionId={uuid}&page=0&size=10&sort=createdAt,desc` *(Optional transactionId parameter)* | `200 OK`: `Page<AiRiskAssessmentDto>`                                      |

## Definition of Done

1. Implement the RESTful endpoint described above, with clean code principles in mind and using a fluent and idiomatic
   style. Ensure each feature is fully tested and the APIs provide correct metadata and error messages. Do not define
   custom exceptions if not required. The stream for the ai-assessment should use Server-Sent Events (SSE). The backend
   should stream the progress of the LLM, providing feedback to the frontend; finally the assessment is stored in a
   database table. The assessment is modeled in two tables **risk_final_assessments** and **risk_final_assessments**:
   one represents the aggregated final assessment with a risk leve, a description of the findings and proposed
   recommendations; while the other tracks which rules were triggered during the assessment, with a score computed based
   on the fired risk rule weight multiplied by how much the rule was relevant, a value between 0.00 and 1.00. To control
   the complexity of the LLM task a configuration shuold be added to specify the max number of risk rules to activate,
   prioritized by the most relevant ones (i.e. those that are mostly applicable)
2. Define the PostgreSQL DB schema for the necessary tables, from the specifications, for this phase. Generate a script
   for provisioning data for local testing and demo.
3. The LLM integrated should be fully configurable and allow an offline setup where a wiremock server is used to stub
   the LLM. In order to achieve this a development feature-flag should be provided to record the LLM interactions and
   build the data to be replayed by a wiremock stub.
4. Add a wiremock server in the Docker compose configuration and its specifi folder in the local-development setup. It
   should be able to act as the LLM for offline development and demo purpose. 
5. Implement the Frontend using these APIs. The UI should be simple and easy to interact with. A table should display
   the past assessments per transactions, organized by pages. It should be possible to filter by each column. The live
   processing of an assessment should provide updates about the steps being taken by the backend and LLM interaction, so
   the user has visual feedback; at the end the final assessment should be displayed and the processing events should
   disappear (only the final result matters). 
6. Define architectural rules, with ArchUnit as far as development goes. The rules should ensure that the packaging and
   modules are independent, coherent and keep a reasonable and simple balance between abstraction and concreteness. Do
   not over-engineer or complicate the structure. Ultimately, the project should be idiomatic and immediate. 
7. Ensure relevant logging, without affecting performance, and tracing is in place.
8. The project should build and pass all the defined tests.