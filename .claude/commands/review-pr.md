---
description: thorough, structured code reviews
---

Conduct a comprehensive code review. 

### Preparation

Before starting the review, ensure you have analyzed the scope of changes:
1. Check for **uncommitted changes** in the working directory.
2. Compare the **current branch** with the `main` branch to understand the full set of changes intended for this PR.

### Code Quality

- **Logic and algorithms**: Verify correctness and efficiency
- **Code style**: Check adherence to project conventions and readability
- **Error handling**: Ensure proper error cases are covered
- **Edge cases**: Identify potential boundary conditions or failure modes

### Security & Performance

- **Security vulnerabilities**: Look for common security issues (SQL injection, XSS, etc.)
- **Performance implications**: Identify potential bottlenecks or inefficiencies
- **Resource management**: Check for memory leaks, file handles, connections

### Architecture & Design

- **Design patterns**: Evaluate appropriate use of patterns and abstractions
- **Modularity**: Assess code organization and separation of concerns
- **Maintainability**: Consider long-term maintenance and extensibility

### Testing & Documentation

- **Test coverage**: Verify adequate testing for new functionality
- **Documentation**: Check for necessary code comments and documentation updates
- **Breaking changes**: Identify any breaking changes and migration needs

## Output Format

Provide feedback in the following structure:

1. **Summary**: Brief overview of the changes and overall assessment
2. **Strengths**: Highlight positive aspects of the implementation
3. **Issues**: List specific problems that need attention (categorized by severity)
4. **Suggestions**: Provide actionable recommendations for improvement
5. **Approval Status**: Indicate if the PR is ready to merge or needs changes
