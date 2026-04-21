// SPDX-License-Identifier: MIT
pragma solidity ^0.8.20;

// Deploy via Remix IDE. OpenZeppelin is auto-imported by Remix from GitHub.

import "@openzeppelin/contracts/token/ERC721/ERC721.sol";
import "@openzeppelin/contracts/access/Ownable.sol";

/**
 * @title  IntramurosSouvenir
 * @notice Minimal ERC-721 for the Volver Intramuros AR tour.
 *         Owner (Cloud Function wallet) mints directly to each user
 *         after Firestore confirms 5 missions complete.
 *         All tokens share a single metadata URI (identical souvenirs).
 */
contract IntramurosSouvenir is ERC721, Ownable {

    uint256 private _tokenIds;
    string  private _tokenUri;

    event SouvenirMinted(address indexed user, uint256 tokenId);

    constructor(string memory initialUri)
        ERC721("Intramuros Souvenir", "VOLVER")
        Ownable(msg.sender)
    {
        _tokenUri = initialUri;
    }

    /// All tokens resolve to the same metadata.
    function tokenURI(uint256) public view override returns (string memory) {
        return _tokenUri;
    }

    /// Owner can update metadata after deploy (e.g. real IPFS CID later).
    function setTokenUri(string calldata newUri) external onlyOwner {
        _tokenUri = newUri;
    }

    /// Owner mints directly to a user. Firestore enforces one-per-user upstream.
    function adminMintTo(address to) external onlyOwner returns (uint256) {
        _tokenIds++;
        uint256 tokenId = _tokenIds;
        _safeMint(to, tokenId);
        emit SouvenirMinted(to, tokenId);
        return tokenId;
    }

    function totalMinted() external view returns (uint256) {
        return _tokenIds;
    }
}
