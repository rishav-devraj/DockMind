# AI log

Tool: Claude Code, running one coordinating session and two parallel agents (backend and mobile).

## Prompts that moved us most

1. "Let's break it down for 2 agents, one will work on the AI and backend, the other on the app." This led to a written API contract before any code, so each side could be built and tested alone.
2. "Show list of APIs while committing, since it would be a mono repo they should be aware that another agent is in progress." This made the commit log the coordination channel: each commit lists the API numbers it touches and their status.
3. "Is our architecture sound? What happens when we have 100k orders? Are we extracting the order ID and then checking?" This found the biggest design flaw in the build, described below, and led to the read-first redesign.

## What the AI got wrong

1. Didnt create a .env file for the backend it was acting as if i would be hardcoding GEMINI AI API key in the code. I had to create a .env file and add the GEMINI API key there.
2. It designed the reader to send the model every open purchase order in the prompt, alongside the photo, and the plan and contract approved it. Asking what happens at 100k orders exposed it. Each open PO added about 70 tokens, so the prompt passes the model's roughly 1 million token input limit at about 15,000 open POs, and every read would fail at 100,000. It was also wrong at small scale. On the messy note, where one digit of the PO number is smudged, the model "read" PO-4503 because it could see PO-4503 in the list, and nothing flagged it.

   We changed it to read first, then look up. The model now gets only the photo and returns the PO number exactly as written, or null if any character is unclear. Code finds that PO with one indexed query and links the lines to it by description. The prompt is now a fixed 1,771 characters, whatever the number of POs. A1 and A2 also became paged and searchable, and indexes were added. We checked it by loading 100,004 open POs. A page of POs came back in about 0.15 seconds, a search in under 0.1 seconds, and all nine kit notes read correctly through real Gemini. The messy note now goes to a clerk instead of being guessed.


3. The expo login fiasco, it took me 30 minutes to finally get the app to run on my iphone, there was some auth issue paired with expo login and i was unable to login with the normal method

4. Form data issue while creating upload picture payload on app side was causing requests to fail.

## What we rewrote by hand, and why

To be completed by the person submitting. This must be a real edit.
