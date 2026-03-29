# Claude Code Instructions

## Git Workflow

### Pull Requests
- **ALWAYS ask before merging PRs to main**
- Create PRs but wait for explicit user approval before merging
- Exception: Only merge automatically if the user has explicitly said "merge it" or "approve and merge"

### Branches
- Create feature branches for all changes
- Use descriptive branch names (e.g., `fix/circular-dependency`, `feature/email-processor`)

## AWS Deployment
- Region: ap-northeast-1
- Stack name: official-doc-generator-app-test
- Always verify CloudFormation permissions before deploying

## Project Structure
- Infrastructure: `infra/app-only.yaml` (SAM template)
- Lambda functions: `lambda/` directory
- Java handlers: `src/main/java/com/officialpapers/api/handler/`
- Frontend: `frontend/` directory
