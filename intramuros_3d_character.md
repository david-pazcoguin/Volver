# Intramuros Passport - 3D Historical Figures for AR Game

## Site | Historical Figure | Why This Figure? | Game Character Role

| **Site** | **Historical Figure** | **Why This Figure?** | **Game Character Role** |
|----------|----------------------|----------------------|-------------------------|
| **Fort Santiago** | **José Rizal** | National hero imprisoned here in 1896 before execution; site houses his cell and memorabilia. Central to Philippine independence narrative. [scribd+1] | **Mentor Guide**: Appears in AR as a scholarly figure in barong tagalog. Delivers quests ("Seek the hidden poem"), quizzes on propaganda movement. Rewards: NFT "Rizal's Last Letter" badge. Dialogue: "In this cell, my thoughts turned to freedom..." |
| **Baluarte de San Diego** | **Antonio Sedeño** | Jesuit priest/architect who designed/built the original Nuestra Señora de Guía fort (1586-1587); key in early Spanish defenses. [thequeensescape+1] | **Fortress Builder**: AR model in Jesuit robes, wielding blueprints. Interactive tutorial: Rotate bastion model, simulate cannon fire defense. Rewards: NFT "Bastion Blueprint". Dialogue: "From this tower, we watched the galleons approach..." |
| **Casa Manila** | **Imelda Marcos** | Oversaw 1980s reconstruction as First Lady to preserve colonial heritage; modeled after Binondo merchant homes. [closerlives](https://www.closerlives.com/blog/post/casa-manila-in-photos-a-living-museum-of-philippine-history-and-culture) | **Restorer Host**: Elegant in terno gown, touring recreated rooms. Mini-game: Arrange period furniture. Rewards: NFT "Casa Deed". Dialogue: "This home revives our bahay na bato legacy..." (Tie to modern tourism revival). |
| **Museo de Intramuros** | **Martin Tinio Jr.** | Historian/author involved in 1970s-80s redevelopment; curated exhibits on city walls/history. [philstar](https://www.philstar.com/lifestyle/business-life/2019/05/06/1915248/museo-de-intramuros-preserving-our-collective-memory) | **Storyteller Curator**: In period historian attire, gesturing to artifacts. Puzzle: Match events to timelines. Rewards: NFT "Wall Fragment". Dialogue: "These stones whisper Manila's 400-year saga..." |
| **Centro de Turismo** | **St. Ignatius of Loyola** | Jesuit founder whose ideals inspired the original San Ignacio Church (now rebuilt as hub); embodies education/revitalization theme. [intramuros](https://intramuros.gov.ph/2024/06/09/intramuros-unveils-centro-de-turismo-a-gateway-to-the-past-present-and-future/) | **Visionary Founder**: Robed saint figure overlaying church model. Final boss quest: Reflect on Intramuros' future. Rewards: Master NFT "Walled City Key". Dialogue: "From ruins rises renewal..." |

---

## Implementation Notes
- **3D Models**: Low-poly humanoid figures (~10k-20k tris) with period-accurate attire
- **AR Integration**: ARCore GeoAnchors for GPS-triggered site overlays
- **Gamification**: Sequential stamp collection → Tranvia tour → blockchain NFT minting
- **User Flow**: Scan site → 3D model loads → Character narrates → Interact → Earn NFT badge
