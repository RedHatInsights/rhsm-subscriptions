# Component Tests Doctor

AI-powered test failure investigator for rhsm-subscriptions. Supports Google Gemini (free, cloud) or Ollama (free, local).

## Quick Start

### Build Once, Choose at Runtime

**Build** (includes both Gemini and Ollama):
```bash
mvn clean package
```

### Option A: Google Gemini (Default - Fast & Free)

1. **Get Free API Key**: https://aistudio.google.com/app/apikey
2. **Set Environment Variable**:
   ```bash
   export GEMINI_API_KEY="AIza..."
   ```
3. **Start Services**:
   ```bash
   docker-compose up -d pgvector
   ```
4. **Initialize**:
   ```bash
   java -jar target/*.jar init --rhsm-subscriptions=/path/to/repo
   ```
5. **Investigate**:
   ```bash
   java -jar target/*.jar investigate TestName
   ```

### Option B: Ollama (Local - No API Key Needed)

1. **Start Services**:
   ```bash
   docker-compose up -d
   ```
2. **Pull Model** (first time only):
   ```bash
   docker exec -it swatch-component-tests-ollama-1 ollama pull mistral
   ```
3. **Initialize**:
   ```bash
   java -Dquarkus.profile=ollama -jar target/*.jar init --rhsm-subscriptions=/path/to/repo
   ```
4. **Investigate**:
   ```bash
   java -Dquarkus.profile=ollama -jar target/*.jar investigate TestName
   ```

## Gemini vs Ollama

| Feature | Google Gemini | Ollama (Mistral 7B) |
|---------|---------------|---------------------|
| **Cost** | Free | Free |
| **Speed** | Fast (~2-5s) | Medium (~15-30s) |
| **Quality** | Excellent ⭐⭐⭐⭐⭐ | Very Good ⭐⭐⭐⭐ |
| **Setup** | API key | Docker + 4GB |
| **Internet** | Required | Not required |
| **Privacy** | Data sent to Google | 100% local |
| **Rate Limits** | 1500 req/day, 1M TPM | Unlimited |
| **Function Calling** | Native support | Native support ✓ |

**Recommendation**: 
- **Best choice**: **Gemini** - excellent quality, fast, free
- **For privacy**: **Ollama with Mistral** - native function calling, good quality, fully local

## How It Works

The AI agent uses **function calling** to:
1. Read the test failure from surefire reports
2. Identify the class being tested
3. Read the entire source file to find issues
4. Look for: commented code, typos, extra characters, logic errors
5. Optionally check git history if code looks correct
6. Generate a detailed investigation report

## Commands

### `init` - Initialize Knowledge Base

```bash
# With Gemini (default)
java -jar target/*.jar init --rhsm-subscriptions=/path/to/repo [--force]

# With Ollama
java -Dquarkus.profile=ollama -jar target/*.jar init --rhsm-subscriptions=/path/to/repo
```

Ingests the codebase into the vector database (first time only). Use `--force` to re-ingest.

### `investigate` - Investigate Test Failures

**Single Test:**
```bash
# With Gemini
java -jar target/*.jar investigate TestName [-v]

# With Ollama
java -Dquarkus.profile=ollama -jar target/*.jar investigate TestName [-v]
```

**Entire Module:**
```bash
# With Gemini
java -jar target/*.jar investigate swatch-contracts

# With Ollama
java -Dquarkus.profile=ollama -jar target/*.jar investigate swatch-contracts
```

Investigates test failures using AI + git history + file system access.

Options:
- `-v, --verbose` - Show detailed investigation process
- `--in-folder` - Manually specify test folder
- `--surefire-report` - Custom surefire reports path

### `status` - Show Ingestion Status

```bash
java -jar target/*.jar status
```

Shows ingestion metadata (when initialized, document count, etc.).

## Example Output

```
Investigating test: ContractsComponentTest

Agent analysis:
--------------------------------------------------------------------------------
Problem: The subscription_number field returns "7181111" instead of expected "71811".
Extra "11" characters appended to the subscription number.

Code found in ContractService.java:
```java
public StatusResponse createPartnerContract(PartnerEntitlementsRequest request) {
  // ...
  subscription.setSubscriptionNumber(request.getSubscriptionNumber() + "11"); // BUG: extra "11"
  // ...
}
```

Fix: Remove the "+ \"11\"" from line 245. Should be:
subscription.setSubscriptionNumber(request.getSubscriptionNumber());
--------------------------------------------------------------------------------
```

## Tools Available to AI

The agent can call these functions:

**File Tools:**
- `readFile(filePath)` - Read entire file content
- `searchInFile(filePath, text, contextLines)` - Search with context

**Git Tools:**
- `getRecentCommits(repoPath, baseBranch, limit)` - Get commits in current branch
- `getFilesChangedInCommit(repoPath, commitHash)` - List changed files
- `getFileDiff(repoPath, commitHash, filePath)` - See file diff

**Database Tools:**
- `executeQuery(sql)` - Query test database (read-only)

## Configuration

### Gemini Configuration

File: `src/main/resources/application-gemini.properties`

```properties
quarkus.langchain4j.ai.gemini.api-key=${GEMINI_API_KEY}
quarkus.langchain4j.ai.gemini.chat-model.model-name=gemini-1.5-flash
quarkus.langchain4j.ai.gemini.timeout=60s
```

Available models:
- `gemini-1.5-flash` (recommended - fast, supports tools)
- `gemini-1.5-pro` (more capable, slower)
- `gemini-2.0-flash-exp` (experimental)

### Ollama Configuration

File: `src/main/resources/application-ollama.properties`

```properties
quarkus.langchain4j.ollama.base-url=http://localhost:11434
quarkus.langchain4j.ollama.chat-model.model-id=llama3.2:1b
quarkus.langchain4j.ollama.timeout=120s
```

Available models:
- `llama3.2:1b` (fast, 1.3GB, **poor quality** - not recommended)
- `llama3.2:3b` (better quality, 2GB)
- `llama3.1:8b` (**recommended for Ollama**, 4.7GB, good quality)

**To use llama3.1:8b**:
```bash
# Pull the model
docker exec -it swatch-component-tests-ollama-1 ollama pull llama3.1:8b

# Update application-ollama.properties
quarkus.langchain4j.ollama.chat-model.model-id=llama3.1:8b
```

## Requirements

- Java 21+
- Maven 3.8+
- Docker/Podman (for PostgreSQL + optionally Ollama)
- **For Gemini**: Free API key from https://aistudio.google.com/app/apikey
- **For Ollama**: ~2GB disk space for model

## Services

### For Gemini (Cloud)

```bash
docker-compose up -d pgvector
```

Services:
- **PostgreSQL with pgvector** (port 5433) - stores document embeddings

### For Ollama (Local)

```bash
docker-compose up -d
```

Services:
- **PostgreSQL with pgvector** (port 5433) - stores document embeddings
- **Ollama** (port 11434) - local LLM inference

## Troubleshooting

### Gemini: "API key not found"
```bash
export GEMINI_API_KEY="your-key-here"
```

### Ollama: "Connection refused"
```bash
docker-compose up -d ollama
# Wait 10 seconds for startup
docker exec -it swatch-component-tests-ollama-1 ollama pull llama3.2:1b
```

### "No ingestion found"
```bash
java -jar target/*.jar init --rhsm-subscriptions=/path/to/repo
```

### "No surefire reports found"
```bash
cd /path/to/module
mvn clean verify
```

### Wrong Profile Active
- **Gemini (default)**: Just run `java -jar target/*.jar ...`
- **Ollama**: Add `-Dquarkus.profile=ollama` before `-jar`
- Check active profile in logs: look for "RAG Configuration" message

## Architecture

```
┌─────────────────┐
│  User Command   │
└────────┬────────┘
         │
         ▼
┌─────────────────┐      ┌──────────────┐
│  DoctorAgent    │◄────►│ Gemini/Ollama│
│  (LangChain4j)  │      └──────────────┘
└────────┬────────┘
         │
         ├──► FileTools (read source code)
         ├──► GitTools (check history)
         ├──► DatabaseTools (query test DB)
         │
         ▼
┌─────────────────┐
│    PGVector     │◄─── Knowledge Base (RAG)
│  (Embeddings)   │
└─────────────────┘
```

## License

GNU General Public License v3.0
