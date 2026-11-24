package de.peterspace.cardanodbsyncapi.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.Value;

/** Open and recently closed governance actions, with the votes of one DRep and one pool if asked for. */
@Value
public class GovernanceOverview {
  @NotNull int currentEpoch;

  /** Name the DRep asked for registered with, null if it has none or none was asked for. */
  String drepName;

  /** Newest first. */
  @NotNull List<GovAction> actions;
}
