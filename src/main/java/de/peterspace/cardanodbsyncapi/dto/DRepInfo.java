package de.peterspace.cardanodbsyncapi.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Value;

/** A delegated representative that votes with the ADA delegated to it. */
@Value
public class DRepInfo {
  @NotNull String drepId;

  /** Name the DRep registered with, null if it has none or its metadata could not be read. */
  String name;

  /** What the DRep states as its objectives, cut to a short line. Null if it states none. */
  String objectives;

  /** Lovelace delegated to the DRep in the latest snapshot. */
  @NotNull long votingPower;

  /** Votes on the recent governance actions, the latest vote per action. */
  @NotNull int votesYes;

  @NotNull int votesNo;

  @NotNull int votesAbstain;

  /** How many recent governance actions there are: the open ones and those that ended lately. */
  @NotNull int recentActions;
}
