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

**IMPORTANT: Always use CodePipeline for deployments**
- **NEVER deploy directly using `sam deploy` or `aws cloudformation` commands**
- All deployments must go through the CodePipeline by merging to main branch
- The pipeline automatically builds and deploys changes
- Pipeline stack: `official-doc-generator-test` in `infra/template.yaml`
- Application stack: `official-doc-generator-app-test` in `infra/app-only.yaml`
- Region: ap-northeast-1

### Deployment Process
1. Create a feature branch
2. Make changes and commit
3. Create a pull request
4. After PR approval and merge to main, CodePipeline automatically deploys

## Project Structure
- Infrastructure: `infra/app-only.yaml` (SAM template)
- Pipeline: `infra/template.yaml` (CodePipeline + CodeBuild)
- Lambda functions: `lambda/` directory
- Java handlers: `src/main/java/com/officialpapers/api/handler/`
- Frontend: `frontend/` directory
