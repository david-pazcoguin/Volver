// SPDX-License-Identifier: MIT
pragma solidity ^0.8.20;

// Deploy via Remix IDE or Hardhat.
// Install OpenZeppelin: npm install @openzeppelin/contracts

import "@openzeppelin/contracts/token/ERC721/ERC721.sol";
import "@openzeppelin/contracts/token/ERC721/extensions/ERC721URIStorage.sol";
import "@openzeppelin/contracts/access/Ownable.sol";

/**
 * @title  IntramurosPassport
 * @notice ERC-721 NFT for completing all 5 Intramuros AR missions.
 *
 * Minting flow:
 *   1. Backend verifies the user has completed all 5 missions.
 *   2. Backend calls whitelistAddress(userWallet) — owner pays a tiny gas fee.
 *   3. User calls claimPassport() from their own wallet — USER pays gas.
 *   4. Each address can only mint once.
 *
 * Deploy to Polygon Amoy testnet (chain 80002) for development,
 * then re-deploy to Polygon mainnet (chain 137) for production.
 *
 * After deployment, paste the contract address into:
 *   PolygonService.java → NFT_CONTRACT_ADDRESS
 */
contract IntramurosPassport is ERC721URIStorage, Ownable {

    uint256 private _tokenIds;

    /**
     * IPFS URI pointing to the NFT metadata JSON.
     * Example metadata JSON:
     * {
     *   "name": "Intramuros Passport — Walled City Key",
     *   "description": "Awarded for completing all 5 Intramuros AR missions.",
     *   "image": "ipfs://<YOUR_IMAGE_CID>",
     *   "attributes": [{ "trait_type": "Edition", "value": "Founding Explorer" }]
     * }
     *
     * Upload the image + JSON to Pinata (pinata.cloud) or NFT.Storage,
     * then replace the CID below.
     */
    string public constant PASSPORT_URI = "ipfs://YOUR_METADATA_CID_HERE";

    /// Addresses whitelisted by the backend after mission verification
    mapping(address => bool) public isWhitelisted;

    /// Prevents double-minting
    mapping(address => bool) public hasMinted;

    event AddressWhitelisted(address indexed user);
    event PassportMinted(address indexed user, uint256 tokenId);

    constructor() ERC721("Intramuros Passport", "IPSP") Ownable(msg.sender) {}

    // ─────────────────────────────────────────────────────────────
    // Owner functions
    // ─────────────────────────────────────────────────────────────

    /**
     * @notice Backend calls this after confirming all 5 missions are done.
     *         Only the contract owner (your server wallet) can whitelist.
     * @param  user  The player's Polygon wallet address.
     */
    function whitelistAddress(address user) external onlyOwner {
        isWhitelisted[user] = true;
        emit AddressWhitelisted(user);
    }

    /// Batch whitelist for convenience
    function whitelistBatch(address[] calldata users) external onlyOwner {
        for (uint i = 0; i < users.length; i++) {
            isWhitelisted[users[i]] = true;
            emit AddressWhitelisted(users[i]);
        }
    }

    // ─────────────────────────────────────────────────────────────
    // User function — USER pays gas
    // ─────────────────────────────────────────────────────────────

    /**
     * @notice Call this from your Polygon wallet to receive your NFT.
     *         Your wallet must be whitelisted first (backend handles this).
     *         You pay the gas (fractions of a cent on Polygon).
     */
    function claimPassport() external {
        require(isWhitelisted[msg.sender], "Complete all 5 Intramuros missions first.");
        require(!hasMinted[msg.sender],    "You have already claimed your Passport NFT.");

        hasMinted[msg.sender] = true;
        _tokenIds++;
        uint256 tokenId = _tokenIds;

        _safeMint(msg.sender, tokenId);
        _setTokenURI(tokenId, PASSPORT_URI);

        emit PassportMinted(msg.sender, tokenId);
    }

    // ─────────────────────────────────────────────────────────────
    // View helpers
    // ─────────────────────────────────────────────────────────────

    function totalMinted() external view returns (uint256) {
        return _tokenIds;
    }
}
