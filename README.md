# Set Game

## Overview
This project is an implementation of the Set Game in Java. The game involves identifying sets of cards based on specific properties. It supports both human and bot players and allows for multi-threaded gameplay.

## Configuration
You can configure the game settings by modifying the `config.properties` file located at: 
Set_Game/src/main/resources/config.properties

Here, you can adjust:
- Number of human players
- Number of bot players
- Freeze timers
- Table delay
- Other gameplay settings

## Game End Conditions
The game ends when:
- There are no more cards left in the deck, or
- No more sets can be found on the table.

## Project Structure
- **logs/**: Contains logs for debugging purposes (not included in the repository).
- **set-game/**: Contains the `.gitattributes` file.
- **target/**: Holds images and other resources needed for the game.
- **src/**:
  - **test/**: Unit tests for the game.
  - **main/**:
    - **resources/**: Contains the `config.properties` file for game settings.
    - **java/**: The core Java code implementing the game logic.

## Build and Run
### Prerequisites
- Java 8 or later
- Maven

### Building the project
To build the project, run the following command from the root directory:
```bash
mvn clean install
```

### Running the Game
After building, you can run the game using the following Maven command:
```bash
mvn exec
-Dexec.mainClass="bguspl.set.ex.Main"
```


## Running Tests
Unit tests are located in the `src/test/java` directory. You can run them with:
```bash
mvn test
```


