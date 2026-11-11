# AI log

Tool: Claude Code, running one coordinating session and two parallel agents (backend and mobile).

## Prompts that moved us most

1. "Let's break it down for 2 agents, one will work on the AI and backend, the other on the app." This led to a written API contract before any code, so each side could be built and tested alone.
2. "Show list of APIs while committing, since it would be a mono repo they should be aware that another agent is in progress." This made the commit log the coordination channel: each commit lists the API numbers it touches and their status.
3. To be completed.

## What the AI got wrong

1. Didnt create a .env file for the backend it was acting as if i would be hardcoding GEMINI AI API key in the code. I had to create a .env file and add the GEMINI API key there.
2.

## What we rewrote by hand, and why

To be completed by the person submitting. This must be a real edit.
