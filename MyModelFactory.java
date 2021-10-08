package uk.ac.bris.cs.scotlandyard.model;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import uk.ac.bris.cs.scotlandyard.model.ScotlandYard.Factory;

import javax.annotation.Nonnull;
import java.util.HashSet;
import java.util.Set;

/** cw-model Stage 2: Complete this class */
public final class MyModelFactory implements Factory<Model> {

  @Nonnull
  @Override
  public Model build(GameSetup setup, Player mrX, ImmutableList<Player> detectives) {
    // TODO
    //		throw new RuntimeException("Implement me!");
    return new MyModelImpl(setup, mrX, detectives);
  }

  class MyModelImpl implements Model {

    GameSetup setup;
    Player mrX;
    ImmutableList<Player> detectives;
    Board.GameState gameState;
    ImmutableSet<Observer> observers;

    public MyModelImpl(GameSetup setup, Player mrX, ImmutableList<Player> detectives) {
      this.setup = setup;
      this.mrX = mrX;
      this.detectives = detectives;
      MyGameStateFactory gameStateFactory = new MyGameStateFactory();
      gameState = gameStateFactory.build(setup, mrX, detectives);
      observers = ImmutableSet.of();
    }

    @Nonnull
    @Override
    public Board getCurrentBoard() {
      return gameState;
    }

    @Override
    public void registerObserver(@Nonnull Observer observer) {

      if (observers.contains(observer)) {
        throw new IllegalArgumentException();
      }

      Set<Observer> observerSet = new HashSet<>(observers);
      observerSet.add(observer);
      observers = ImmutableSet.copyOf(observerSet);
    }

    @Override
    public void unregisterObserver(@Nonnull Observer observer) {
      if (observer == null) {
        throw new NullPointerException();
      }

      if (!observers.contains(observer)) {
        throw new IllegalArgumentException();
      }

      Set<Observer> observerSet = new HashSet<>(observers);
      if (observerSet.contains(observer)) {
        observerSet.remove(observer);
      }
      observers = ImmutableSet.copyOf(observerSet);
    }

    @Nonnull
    @Override
    public ImmutableSet<Observer> getObservers() {
      return observers;
    }

    @Override
    public void chooseMove(@Nonnull Move move) {
      gameState = gameState.advance(move); // update game state by move
      // observer callback onModelChanged
      if (gameState.getWinner().isEmpty()) {
        // no winner, move made
        observers.forEach(observer -> observer.onModelChanged(gameState, Observer.Event.MOVE_MADE));
      } else {

        // game over
        observers.forEach(observer -> observer.onModelChanged(gameState, Observer.Event.GAME_OVER));
      }
    }
  }
}
