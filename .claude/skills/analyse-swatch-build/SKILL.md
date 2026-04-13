  ---                                                                                                                                                                        
name: analyse-swatch-build
description: Run the full Maven build and analyze warnings and errors.
disable-model-invocation: true                                                                                                                                             
allowed-tools: Bash(mvnw *)
  ---                                                                                                                                                                        

Run the full Maven build and analyze the output for warnings and errors.

1. From ./ run: ./mvnw clean install -DskipTests and monitor output for warnings/errors
2. Format a report of all issues found.
3. For the RECOMMENDED ACTIONS section, provide JIRA-ready details for each item including:
   - Priority level
   - Files to modify with line numbers
   - Current deprecated/problematic usage
   - Detailed steps to resolve
   - Acceptance criteria
   - References/documentation links where applicable
   
The goal is to provide enough detail that each recommendation can be directly converted 
into a JIRA ticket without additional research.
