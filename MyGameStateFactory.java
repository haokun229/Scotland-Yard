package uk.ac.bris.cs.scotlandyard.model;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import uk.ac.bris.cs.scotlandyard.model.Board.GameState;
import uk.ac.bris.cs.scotlandyard.model.ScotlandYard.Factory;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.stream.Collectors;

/** cw-model Stage 1: Complete this class */
public final class MyGameStateFactory implements Factory<GameState> {

  @Nonnull
  @Override
  public GameState build(GameSetup setup, Player mrX, ImmutableList<Player> detectives) {
    // TODO
    //		throw new RuntimeException("Implement me!");

    // empty graph or empty round
    if (setup.graph.edges().isEmpty() || setup.rounds.size() == 0) {
      throw new IllegalArgumentException();
    }

    // mrX is null || detective is null
    if (mrX == null || detectives == null) {
      throw new NullPointerException();
    }

    // no mrX || swapper mrX
    if (!mrX.isMrX()) {
      throw new IllegalArgumentException();
    }

    Set<Piece> pieces;

    // any detective null
    List<Piece> pieceList =
        detectives.stream().map(e -> e.piece()).filter(e -> e != null).collect(Collectors.toList());
    if (pieceList.size() != detectives.size()) {
      throw new NullPointerException();
    }

    // more than one mrX || swapper mrX
    pieces =
        detectives.stream().map(e -> e.piece()).filter(e -> e.isMrX()).collect(Collectors.toSet());
    if (!pieces.isEmpty()) {
      throw new IllegalArgumentException();
    }

    // duplicate player
    pieces = detectives.stream().map(e -> e.piece()).collect(Collectors.toSet());
    if (pieces.size() != detectives.size()) {
      throw new IllegalArgumentException();
    }

    Set<Integer> locations;

    // detective's location overlap
    locations = detectives.stream().map(e -> e.location()).collect(Collectors.toSet());
    if (locations.size() != detectives.size()) {
      throw new IllegalArgumentException();
    }

    // detective has secret ticket or double ticket
    Set<Player> players =
        detectives.stream()
            .filter(
                player ->
                    player.has(ScotlandYard.Ticket.SECRET)
                        || player.has(ScotlandYard.Ticket.DOUBLE))
            .collect(Collectors.toSet());
    if (!players.isEmpty()) {
      throw new IllegalArgumentException();
    }
    return new MyGameStateImpl(setup, mrX, detectives);
  }

  class MyGameStateImpl implements GameState {

    GameSetup setup; // setup of game
    Player mrX; // mrX' info
    ImmutableList<Player> detectives; // detectives' info
    List<LogEntry> mrXTravelLog; // mrX's travel log
    int round = 0; // which round now
    boolean mrX_moved = false; // mrX moved?
    Set<Piece> movedPiece; // played detectives

    public MyGameStateImpl(GameSetup setup, Player mrX, ImmutableList<Player> detectives) {
      this.setup = setup;
      this.mrX = mrX;
      this.mrXTravelLog = new ArrayList<>();
      this.detectives = detectives;
      mrXTravelLog = new ArrayList<>();
      movedPiece = new HashSet<>();
    }

    @Nonnull
    @Override
    public GameSetup getSetup() {
      return setup;
    }

    @Nonnull
    @Override
    public ImmutableSet<Piece> getPlayers() {
      Set<Piece> set = new HashSet<>();
      set.add(mrX.piece());
      detectives.forEach(player -> set.add(player.piece()));
      return ImmutableSet.copyOf(set);
    }

    @Nonnull
    @Override
    public Optional<Integer> getDetectiveLocation(Piece.Detective detective) {
      for (Player p : detectives) { // iterate to find detective in detectives'
        if (Objects.equals(detective, p.piece())) {
          return Optional.of(p.location());
        }
      }
      return Optional.empty();
    }

    @Nonnull
    @Override
    public Optional<TicketBoard> getPlayerTickets(Piece piece) {
      if (Objects.equals(piece, mrX.piece())) {
        return Optional.ofNullable(mrX.ticketBoard());
      }

      for (Player detective : detectives) {
        if (Objects.equals(detective.piece(), piece)) {
          return Optional.of(detective.ticketBoard());
        }
      }
      return Optional.empty();
    }

    @Nonnull
    @Override
    public ImmutableList<LogEntry> getMrXTravelLog() {
      return ImmutableList.copyOf(mrXTravelLog);
    }

    private boolean roundFinished() {
      return movedPiece.size() == detectives.size(); // all player moved
    }

    @Nonnull
    @Override
    public ImmutableSet<Piece> getWinner() {

      { // check detectives win

        // check mrX caught by detectives
        List<Player> players =
            detectives.stream()
                .filter(d -> d.location() == mrX.location())
                .collect(Collectors.toList());

        // check mrX has no ticket to move
        ImmutableSet<ScotlandYard.Transport> transports = setup.stationTransport(mrX.location());
        List<ScotlandYard.Transport> transportList =
            transports.stream()
                .filter(
                    transport ->
                        mrX.has(ScotlandYard.Ticket.SECRET) || mrX.has(transport.requiredTicket()))
                .collect(Collectors.toList());

        // mrX cornered by detectives
        Set<Integer> locations =
            setup.graph.adjacentNodes(mrX.location()); // get mrX's adjacent location

        Set<Integer> detectivesLocation =
            detectives.stream()
                .map(d -> d.location())
                .collect(Collectors.toSet()); // all detectives' location

        if (!players.isEmpty() // no one catch mrX
            || transportList.isEmpty() // mrX stuck
            || detectivesLocation.containsAll(locations) // detectives corn mrX
        ) {
          return ImmutableSet.copyOf(
              detectives.stream().map(d -> d.piece()).collect(Collectors.toList()));
        }
      }

      { // check mrX win
        List<Player> players = //
            detectives.stream()
                .filter(
                    detective -> {
                      ImmutableSet<ScotlandYard.Transport> transports =
                          setup.stationTransport(detective.location());
                      List<ScotlandYard.Transport> transportList =
                          transports.stream()
                              .filter(t -> detective.has(t.requiredTicket()))
                              .collect(Collectors.toList());
                      return !transportList.isEmpty(); // filter detective has no transport to move
                    })
                .collect(Collectors.toList());
        if (players.isEmpty() // all detectives stuck
            || roundFinished() && round >= setup.rounds.size() // round exceed
        ) {
          return ImmutableSet.of(mrX.piece());
        }
      }

      return ImmutableSet.of(); // no winner
    }

    @Nonnull
    @Override
    public ImmutableSet<Move> getAvailableMoves() {

      if (!this.getWinner().isEmpty()) { // Game Over, no one can move
        return ImmutableSet.of();
      }

      // mrX's move round
      if (!mrX_moved) {
        return ImmutableSet.copyOf(mrXAvailableMove());

      } else {

        // detectives' move
        Set<Move> moves = new HashSet<>();
        detectives.forEach(
            player -> {
              if (!movedPiece.contains(player.piece())) { // filter moved piece
                moves.addAll(playerSingleMoves(player));
              }
            });

        if (moves.isEmpty()) { // all detectives can't move, set mrX to move
          mrX_moved = false;
          movedPiece = new HashSet<>();
          return this.getAvailableMoves();
        }

        return ImmutableSet.copyOf(moves);
      }
    }

    /**
     * mrX available move
     *
     * @return
     */
    private Set<Move> mrXAvailableMove() {

      Set<Move> moves = new HashSet<>();

      if (mrX.has(ScotlandYard.Ticket.DOUBLE)
          && round + 1
              < setup.rounds
                  .size()) { // mrX double move: double move ticket and next round is valid
        Set<Integer> adjacentNodes1 =
            setup.graph.adjacentNodes(mrX.location()); // all adjacent node to mrX current location

        for (Integer d1 : adjacentNodes1) { //
          Set<Integer> adjacentNode2 =
              setup.graph.adjacentNodes(d1); // all adjacent node to intermediate station d1
          for (Integer d2 : adjacentNode2) {
            ImmutableSet<ScotlandYard.Transport> transports1 =
                setup.graph.edgeValue(mrX.location(), d1).get();
            ImmutableSet<ScotlandYard.Transport> transports2 = setup.graph.edgeValue(d1, d2).get();
            for (ScotlandYard.Transport t1 : transports1) {
              for (ScotlandYard.Transport t2 : transports2) {
                if (Objects.equals(t1, t2)) {
                  if (mrX.hasAtLeast(
                      t1.requiredTicket(), 2)) { // move to d2 by d1, two move require same ticket
                    moves.add(
                        new Move.DoubleMove(
                            mrX.piece(),
                            mrX.location(),
                            t1.requiredTicket(),
                            d1,
                            t2.requiredTicket(),
                            d2));
                  }

                  if (mrX.hasAtLeast(
                      ScotlandYard.Ticket.SECRET,
                      2)) { // move to d2 by d1, two move use secret ticket
                    moves.add(
                        new Move.DoubleMove(
                            mrX.piece(),
                            mrX.location(),
                            ScotlandYard.Ticket.SECRET,
                            d1,
                            ScotlandYard.Ticket.SECRET,
                            d2));
                  }

                  if (mrX.has(ScotlandYard.Ticket.SECRET)
                      && mrX.has(t1.requiredTicket())) { // move to d2 by d1, use one secret
                    moves.add(
                        new Move.DoubleMove(
                            mrX.piece(),
                            mrX.location(),
                            t1.requiredTicket(),
                            d1,
                            ScotlandYard.Ticket.SECRET,
                            d2));

                    moves.add(
                        new Move.DoubleMove(
                            mrX.piece(),
                            mrX.location(),
                            ScotlandYard.Ticket.SECRET,
                            d1,
                            t1.requiredTicket(),
                            d2));
                  }
                } else {
                  if (mrX.has(t1.requiredTicket())
                      && mrX.has(
                          t2.requiredTicket())) { // move to d2 by d1, two move require different
                    // ticket
                    moves.add(
                        new Move.DoubleMove(
                            mrX.piece(),
                            mrX.location(),
                            t1.requiredTicket(),
                            d1,
                            t2.requiredTicket(),
                            d2));
                  }

                  if (mrX.has(t1.requiredTicket())
                      && mrX.has(
                          ScotlandYard.Ticket
                              .SECRET)) { // move to d2 by d1, to d1 use required ticket, to d2 use
                                          // one secret ticket
                    moves.add(
                        new Move.DoubleMove(
                            mrX.piece(),
                            mrX.location(),
                            t1.requiredTicket(),
                            d1,
                            ScotlandYard.Ticket.SECRET,
                            d2));
                  }

                  if (mrX.has(ScotlandYard.Ticket.SECRET)
                      && mrX.has(
                          t2
                              .requiredTicket())) { // move to d2 by d1, to d1 use secret ticket, to
                                                    // d2 use required ticket
                    moves.add(
                        new Move.DoubleMove(
                            mrX.piece(),
                            mrX.location(),
                            ScotlandYard.Ticket.SECRET,
                            d1,
                            t2.requiredTicket(),
                            d2));
                  }

                  if (mrX.hasAtLeast(ScotlandYard.Ticket.SECRET, 2)) { // use two secret ticket.
                    moves.add(
                        new Move.DoubleMove(
                            mrX.piece(),
                            mrX.location(),
                            ScotlandYard.Ticket.SECRET,
                            d1,
                            ScotlandYard.Ticket.SECRET,
                            d2));
                  }
                }
              }
            }
          }
        }
      }

      // filter occupied  station move
      moves =
          moves.stream()
              .filter(
                  move -> {
                    Move.DoubleMove doubleMove = (Move.DoubleMove) move;
                    Set<Integer> locations =
                        detectives.stream().map(d -> d.location()).collect(Collectors.toSet());
                    return !locations.contains(
                            doubleMove.destination1) // filter d1 occupied detective
                        && !locations.contains(
                            doubleMove.destination2); // filter d2 occupied detective
                  })
              .collect(Collectors.toSet());

      moves.addAll(playerSingleMoves(mrX)); // mrXSingleMove

      return moves;
    }
    /**
     * player's available single moves
     *
     * @param player the player
     * @return
     */
    private Set<Move> playerSingleMoves(Player player) {
      Set<Move> moves = new HashSet<>();

      Set<Integer> adjacentNodes =
          setup.graph.adjacentNodes(
              player.location()); // all adjacent node to player current location

      for (Integer node : adjacentNodes) {
        ImmutableSet<ScotlandYard.Transport> transports =
            setup
                .graph
                .edgeValue(player.location(), node)
                .get(); // all transport from player's location to adjacent node
        for (ScotlandYard.Transport transport : transports) {
          if (player.has(transport.requiredTicket())) { // player has ticket to move by transport
            Move move =
                new Move.SingleMove(
                    player.piece(), player.location(), transport.requiredTicket(), node);
            moves.add(move);
          }
          if (player.isMrX()
              && player.has(
                  ScotlandYard.Ticket.SECRET)) { // player is mrX and use secret ticket to move
            Move move =
                new Move.SingleMove(
                    player.piece(), player.location(), ScotlandYard.Ticket.SECRET, node);
            moves.add(move);
          }
        }
      }

      // filter occupied station
      moves =
          moves.stream()
              .filter(
                  move -> {
                    Move.SingleMove singleMove = (Move.SingleMove) move;
                    Set<Integer> locations =
                        detectives.stream().map(d -> d.location()).collect(Collectors.toSet());
                    return !locations.contains(
                        singleMove.destination); // filter station occupied by detectives
                  })
              .collect(Collectors.toSet());

      return moves;
    }

    @Nonnull
    @Override
    public GameState advance(Move move) {

      { // check move valid
        if (move instanceof Move.SingleMove) {
          Move.SingleMove singleMove = (Move.SingleMove) move;

          Set<Integer> adjacentNodes = setup.graph.adjacentNodes(move.source());
          if (!adjacentNodes.contains(singleMove.destination)) { // wrong adjacent node
            throw new IllegalArgumentException();
          }

          ImmutableSet<ScotlandYard.Transport> transports =
              setup.graph.edgeValue(move.source(), ((Move.SingleMove) move).destination).get();
          Set<ScotlandYard.Transport> transportSet =
              transports.stream()
                  .filter(
                      transport ->
                          singleMove.ticket == ScotlandYard.Ticket.SECRET
                              || transport.requiredTicket()
                                  == singleMove.ticket) // filter wrong ticket
                  .collect(Collectors.toSet());
          if (transportSet.isEmpty()) {
            throw new IllegalArgumentException();
          }
        } else {
          Move.DoubleMove doubleMove = (Move.DoubleMove) move;
          Set<Integer> adjacentNodes1 = setup.graph.adjacentNodes(doubleMove.source());
          Set<Integer> adjacentNodes2 = setup.graph.adjacentNodes(doubleMove.destination1);
          if (!adjacentNodes1.contains(doubleMove.destination1) // wrong adjacent node
              || !adjacentNodes2.contains(doubleMove.destination2)) {
            throw new IllegalArgumentException();
          }
          ImmutableSet<ScotlandYard.Transport> t1 =
              setup.graph.edgeValue(doubleMove.source(), doubleMove.destination1).get();
          ImmutableSet<ScotlandYard.Transport> t2 =
              setup.graph.edgeValue(doubleMove.destination1, doubleMove.destination2).get();
          Set<ScotlandYard.Transport> transportSet1 =
              t1.stream()
                  .filter(
                      transport ->
                          doubleMove.ticket1 == ScotlandYard.Ticket.SECRET
                              || transport.requiredTicket()
                                  == doubleMove.ticket1) // filter wrong ticket
                  .collect(Collectors.toSet());

          Set<ScotlandYard.Transport> transportSet2 =
              t2.stream()
                  .filter(
                      transport ->
                          doubleMove.ticket2 == ScotlandYard.Ticket.SECRET
                              || transport.requiredTicket()
                                  == doubleMove.ticket2) // filter wrong ticket
                  .collect(Collectors.toSet());

          if (transportSet1.isEmpty() || transportSet2.isEmpty()) {
            throw new IllegalArgumentException();
          }
        }
      }

      if (roundFinished()) { // new round
        mrX_moved = false;
        movedPiece = new HashSet<>();
      }

      // update GameState here
      var gameState = this;

      if (move.commencedBy().isMrX()) { // process mrX's move
        if (move instanceof Move.SingleMove) { // single move
          Move.SingleMove singleMove = (Move.SingleMove) move;
          mrX = mrX.at(singleMove.destination); // update location
          mrX = mrX.use(singleMove.ticket); // use ticket
          if (setup.rounds.get(round)) { // add travel log
            mrXTravelLog.add(LogEntry.reveal(singleMove.ticket, singleMove.destination));
          } else {
            mrXTravelLog.add(LogEntry.hidden(singleMove.ticket));
          }
          round++;
        } else { // double move
          Move.DoubleMove doubleMove = (Move.DoubleMove) move;
          mrX = mrX.at(doubleMove.destination2); // update location

          for (ScotlandYard.Ticket ticket : doubleMove.tickets()) { // use ticket to move
            mrX = mrX.use(ticket);
          }

          if (setup.rounds.get(round)) { // add first move log
            mrXTravelLog.add(LogEntry.reveal(doubleMove.ticket1, doubleMove.destination1));
          } else {
            mrXTravelLog.add(LogEntry.hidden(doubleMove.ticket1));
          }

          round++;

          if (setup.rounds.get(round)) { // add second move log
            mrXTravelLog.add(LogEntry.reveal(doubleMove.ticket2, doubleMove.destination2));
          } else {
            mrXTravelLog.add(LogEntry.hidden(doubleMove.ticket2));
          }

          round++;
        }
        mrX_moved = true;
      } else { // process detective
        Move.SingleMove singleMove = (Move.SingleMove) move;
        List<Player> players =
            detectives.stream()
                .map( // map to find detective
                    player -> {
                      if (Objects.equals(
                          player.piece(), move.commencedBy())) { // update detective state
                        player = player.at(singleMove.destination);
                        return player.use(singleMove.ticket);
                      }
                      return player;
                    })
                .collect(Collectors.toList());
        detectives = ImmutableList.copyOf(players);
        mrX = mrX.give(singleMove.ticket); // give used ticket to mrX
        movedPiece.add(move.commencedBy());
      }

      return gameState;
    }
  }
}
