# Volver Al Pasado 10-Minute Presentation Script

Alignment note: the PowerPoint frames the study around 5 official Intramuros passport landmarks. The current app build in this repo shows 6 missions because it also includes an LPU-based testing mission. That 6th mission is an internal testing ground used for faster on-campus validation before going out to the actual Intramuros heritage sites. The script below stays aligned with the research deck and the main app flow, while avoiding unnecessary emphasis on that testing mission unless the panel asks.

Suggested pacing: around 45 to 55 seconds per slide.

## Slide 1 – Title
Script:
"Good day, panelists. We are presenting Volver Al Pasado, a mobile application designed to enhance heritage tourism in Intramuros through gamified augmented reality and blockchain-secured rewards. In simple terms, our app turns a normal heritage visit into an interactive mission-based experience where users go to specific landmarks, collect relics, complete location-based tasks, and receive a digital souvenir at the end. Our study focuses on how technology can make historical engagement more interactive, especially for younger and more digitally active visitors."

## Slide 2 – The Focus
Script:
"Our app is built on three main technologies working together. First is gamification, where users complete missions across heritage sites instead of just passively reading information. Second is augmented reality, which lets the app place 3D objects into the real environment so the visit feels more interactive and immersive. Third is blockchain, which gives users a verifiable digital reward in the form of an NFT souvenir after completing the experience. So the app is not positioned as a guided tour with a person explaining every site. Instead, it is a self-directed mission experience that encourages users to visit the place, complete the objective, and then explore more on their own."

## Slide 3 – The Context
Script:
"This study is inspired by the Intramuros Passport Program, which already proved that visitors enjoy collecting stamps from different heritage sites. What we did was extend that idea into a digital format. Instead of collecting only physical stamps, users of our app go to the site, unlock the mission, collect relics, and earn digital collectibles after completing that location. In short, Volver Al Pasado is a digital mission companion to the existing passport concept, not a replacement for it."

## Slide 4 – The Challenge
Script:
"The main problem we are addressing is that traditional heritage tourism methods are often not enough to sustain engagement, especially for younger audiences. Static signboards and standard guided tours can be informative, but they are not always interactive or memorable. Another issue is location accuracy, because ordinary GPS can be unreliable in dense areas like Intramuros. Lastly, many existing local tourism apps give temporary rewards like points, but not something lasting or personally owned by the visitor. Our app responds to all three of those challenges."

## Slide 5 – Literature Gap
Script:
"When we reviewed related studies and applications, we found that existing systems usually solved only one part of the problem. Some apps had navigation, some had AR, and some international systems used blockchain rewards, but none combined all three for Philippine heritage tourism. That is where Volver Al Pasado contributes something new. Our app combines geospatial AR, game-based exploration, and blockchain-backed rewards in one experience that is tailored to Intramuros."

## Slide 6 – Architecture
Script:
"Behind the app, there are three layers working together. The first is the AR and geospatial layer, which checks whether the user is near the landmark and then places the digital content in the correct real-world location. The second is the cloud layer, where Firebase handles login, stores mission progress, and verifies completed tasks. The third is the blockchain layer, which creates the NFT souvenir once the user finishes the required missions. For non-technical users, the easiest way to think about this is: the phone handles the experience, the cloud tracks the progress, and the blockchain secures the final reward."

## Slide 7 – Methodology
Script:
"For our methodology, we followed an iterative prototype model. We started with requirements gathering and planning, then moved to design, implementation, testing, and refinement. This means we did not build everything at once. We improved the app step by step based on technical testing and user feedback. In terms of tools, we used native Android development, ARCore for augmented reality, Firebase for the backend, and Polygon for blockchain integration."

## Slide 8 – App Walkthrough
Script:
"This slide shows the actual flow of the app. First, the app checks if the user is within the allowed range of a heritage site before opening the AR mission. Second, once the user enters AR view, the app presents the mission elements needed for that location, especially the relic collection experience. Third, when the user collects the relics and completes the mission, the app saves that progress in the database. Finally, after all required missions are completed, the system mints an NFT souvenir to the user’s wallet. So the present implementation is focused on going to the location, accomplishing the mission there, and then letting the user freely explore the place after completion."

## Slide 9 – Evaluation
Script:
"To evaluate the system, we gathered responses from 60 participants from LPU Manila. We used three established instruments: ISO/IEC 25010 for software quality, TAM for technology acceptance, and SUS for usability. These tools allowed us to measure not only whether the app worked, but also whether users found it useful, understandable, engaging, and worth adopting. This is important because a heritage app should not only be technically functional, it also needs to feel practical and enjoyable for real users."

## Slide 10 – Results Part 1
Script:
"For software quality, the app received an aggregate mean of 4.34 out of 5, which indicates a strong overall evaluation. All five quality categories scored above 4.30, and the highest score was in usability or interaction capability. This tells us that users generally found the app easy to use and well-designed. For us, this is an important result because even advanced features like AR and NFT rewards only matter if users can navigate the app comfortably."

## Slide 11 – Results Part 2
Script:
"The second part of the results shows that the app was not only acceptable, but also commercially promising. More than half of the participants said they were willing to pay, with a median price of 125 pesos, which suggests potential as a paid add-on or tourism package feature. The AR experience quality and revisit intention scores were also high, meaning the app was able to encourage both enjoyment and interest in returning. Overall, the results suggest that this concept is not just academically valid, but also practical for real-world tourism use."

## Slide 12 – Conclusion
Script:
"In conclusion, our study shows that combining gamified AR, accurate location-based interaction, and blockchain-secured rewards can improve cultural heritage engagement in a meaningful way. Volver Al Pasado is not currently a full tour guide app. Its present implementation is a mission-based heritage experience where the user visits the site, completes the objective, and is then encouraged to continue exploring the place. Moving forward, we recommend expanding to Polygon mainnet, supporting iOS users, conducting a longer-term behavioral study, and exploring a pilot partnership with the Intramuros Administration. Thank you, and we are now ready for your questions."

## Short Delivery Tips
- Keep your tone conversational, like you are guiding the panel through the user journey.
- When showing the app, use simple phrases like "the app checks location," "the character appears on screen," and "the reward is saved digitally."
- If you need to clarify the current scope, say: "At this stage, the app is not yet a full tour guide app. The current implementation is mission-based: users go to the site, collect relics, complete the location, and then explore the place afterward."
- If a panelist is non-technical, avoid deep terms unless they ask. Replace them with "digital verification," "real-world overlay," or "secure digital souvenir."
- If asked about blockchain, the shortest safe explanation is: "It allows the reward to be unique, traceable, and owned by the user."
- If asked why the app has 6 missions, you can say: "The research design is based on 5 official Intramuros missions. The 6th mission in LPU is only a testing ground so we can validate features faster before field testing in the actual sites."
