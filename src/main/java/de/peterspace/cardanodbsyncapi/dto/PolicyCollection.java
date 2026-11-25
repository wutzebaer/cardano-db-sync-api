package de.peterspace.cardanodbsyncapi.dto;

import lombok.Value;

/** What the owner of a policy registered about its collection on chain (CIP-88). */
@Value
public class PolicyCollection {
  /** Name of the collection as its owner wrote it. Nobody checked it. */
  String name;

  /** What the owner wrote about the collection, if anything. */
  String description;

  /** Address of a picture for the collection as its owner wrote it, e.g. "ipfs://…". */
  String image;
}
