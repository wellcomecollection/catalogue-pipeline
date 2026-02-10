-- Proposed schema from RFC 083: Stable identifiers following mass record migration
-- Two-table schema: canonical_ids (ID registry + pre-generation pool)
--                   identifiers  (source ID → canonical ID mappings)

-- Step 1: Canonical ID registry
CREATE TABLE `canonical_ids` (
  `CanonicalId` varchar(8) NOT NULL PRIMARY KEY,
  `Status` ENUM('free', 'assigned') NOT NULL DEFAULT 'free',
  `CreatedAt` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  INDEX `idx_free` (`Status`, `CanonicalId`)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;

-- Step 2: Source ID → Canonical ID mappings
CREATE TABLE `identifiers` (
  `OntologyType` varchar(255) NOT NULL,
  `SourceSystem` varchar(255) NOT NULL,
  `SourceId` varchar(255) NOT NULL,
  `CanonicalId` varchar(8) NOT NULL,
  `CreatedAt` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`OntologyType`, `SourceSystem`, `SourceId`),
  FOREIGN KEY (`CanonicalId`) REFERENCES `canonical_ids`(`CanonicalId`),
  INDEX `idx_canonical` (`CanonicalId`)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;
