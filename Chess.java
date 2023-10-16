// Isaac Wu
// Reviewed by Jay Dharmadhikari
// Playtested by Paul Sawyer
// Instructions heavily inspired by https://en.wikipedia.org/wiki/Chess#Rules
// CSE 123
// C0: Abstract Strategy Games

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

// A class to represent a game of Chess that implements the AbstractStrategyGame interface.
public class Chess implements AbstractStrategyGame {
    // Map to convert from abbreviations to piece names
    private static final HashMap<String, String> ABBVS = new HashMap<>(Map.ofEntries(
        Map.entry("K", "King"),
        Map.entry("Q", "Queen"),
        Map.entry("R", "Rook"),
        Map.entry("N", "Knight"),
        Map.entry("B", "Bishop"),
        Map.entry("P", "Pawn")
    ));
    // Array to let Color be iterable
    private final Piece.Color[] C = {Piece.Color.WHITE, Piece.Color.BLACK};
    private Board board;
    // Map<"Piece Name", Map<Color, ArrayList<Piece>>>
    private Map<String, Map<Piece.Color, ArrayList<Piece>>> pieces;
    // Log of moves to print once the game is over
    private String log;
    // Which Square en passant can be performed on
    private Square ep;
    // If both Players agree to a draw
    private int draw;
    // Tracker for 50-move rule
    private int draw50;
    private boolean resigned;
    private int moves;

    /**
     * Constructs a new Chess game based off of the input FEN string.
     * @param FEN (Forsyth-Edwards Notation) input
     */
    public Chess(String FEN) {
        board = new Board();
        pieces = new HashMap<>();
        log = "";
        draw = 0;
        draw50 = 0;
        resigned = false;
        moves = 0;

        Map<Piece.Color, ArrayList<Piece>> kings = new HashMap<>();
        Map<Piece.Color, ArrayList<Piece>> queens = new HashMap<>();
        Map<Piece.Color, ArrayList<Piece>> rooks = new HashMap<>();
        Map<Piece.Color, ArrayList<Piece>> knights = new HashMap<>();
        Map<Piece.Color, ArrayList<Piece>> bishops = new HashMap<>();
        Map<Piece.Color, ArrayList<Piece>> pawns = new HashMap<>();
        pieces.put("King", kings);
        pieces.put("Queen", queens);
        pieces.put("Rook", rooks);
        pieces.put("Knight", knights);
        pieces.put("Bishop", bishops);
        pieces.put("Pawn", pawns);

        // FEN limitations:
        // Castling specification doesn't work if rooks are not on the edges of the screen,
        // which shouldn't be a problem...?
        String[] s = FEN.split("/| ");
        int offset;
        for(int row = 0; row < 8; row++) {
            offset = 0;
            // Iterate over each character
            for(int i = 0; i < s[row].length(); i++) {
                char c = s[row].charAt(i);
                if('1' <= c && c <= '8') {
                    // If it's a number, set a new offset to add Pieces
                    offset += Integer.parseInt(""+c) - 1;
                } else {
                    // Find square
                    Square sqr = board.getSquare((char)('a' + i + offset), 8 - row);
                    Piece.Color clr = ('B' <= c && c <= 'R') ? C[0] : C[1];
                    c = Character.toUpperCase(c);
                    sqr.piece = constructPiece(ABBVS.get(""+c), clr, sqr);
                    if(c == 'K') {
                        ((King) sqr.piece).canCastle =
                                s[9].contains(clr.equals(C[0]) ? "K" : "k") ||
                                s[9].contains(clr.equals(C[0]) ? "Q" : "q");
                    } else if(c == 'R') {
                        sqr.piece = new Rook(clr, sqr);
                        if(sqr.file == 'a' && s[9].contains(clr.equals(C[0]) ? "Q" : "q")) {
                            ((Rook) sqr.piece).canCastle = true;
                        } else if(sqr.file == 'h' && s[9].contains(clr.equals(C[0]) ? "K" : "k")) {
                            ((Rook) sqr.piece).canCastle = true;
                        }
                    }
                    add(pieces.get(ABBVS.get(""+c)), clr, sqr.piece);
                }
                // if(offset > 0) { offset--; }
            }
        }
        int fullMoves = Integer.parseInt(s[12]) - 1;
        moves = fullMoves * 2 + (s[8].equals("w") ? 0 : 1);
        ep = board.getSquare(s[10].equals("-") ? "z9" : s[10]);
        draw50 = Integer.parseInt(s[11]);
    }

    /**
     * Constructs a new Chess game with the default start position.
     */
    public Chess() {
        this("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1");
    }

    /**
     * Constructs a new Chess game with the default start position.
     * Also performs all (valid) preMoves as if they were typed in.
     * @param preMoves Array of moves
     */
    public Chess(String[] preMoves) {
        this();
        for(String initMoves : preMoves) {
            makeMove(initMoves);
        }
    }

    /**
     * Parses valid moves using Algebraic Notation.
     * @param input String to parse
     * @throws IllegalArgumentException If input is too short;
     * @throws IllegalArgumentException If there are no more of the specified piece;
     * @throws IllegalArgumentException If two or more pieces can reach the same square
     *                                  but neither is specified;
     * @throws IllegalArgumentException If specified piece cannot reach the input square;
     * @throws IllegalArgumentException If capture is not explicitly called;
     * @throws IllegalArgumentException If capture is specified when there is no piece to capture;
     * @throws IllegalArgumentException If no piece can reach the specified input square;
     * @throws IllegalArgumentException If castle was input (eg. O-O) when castling is impossible;
     * @throws IllegalArgumentException If promotion was input (eg. c8=Q)
     *                                  while pawn is not on the last rank;
     * @throws IllegalArgumentException If moving pawn to the last rank with no promotion
     *                                  piece specified;
     * @throws IllegalArgumentException Or if trying to promote to Pawn or King.
     */
    public void makeMove(String input) {
        if(input.length() < 2) {
            throw new IllegalArgumentException("Input is wrong");
        }
        // Create copy to modify, keep one to log
        String move = new String(input);
        Piece.Color color = C[getNextPlayer() - 1];
        String selected = ABBVS.get(""+move.charAt(0));
        if(selected == null) {
            selected = move.charAt(0) == 'O' ? "Castle" : "Pawn";
        }
        int rankSpecifier = -1;
        char fileSpecifier = '\u0000';
        boolean capturing = false;
        boolean finished = false;
        String promotion = null;

        // Handling special inputs
        if(input.equals("legal")) {
            for(String s : getAllLegalMoves(color)) {
                System.out.println(s);
            }
            finished = true;
            input = "";
            // Doesn't count as a move, just checking legal moves
            moves--;
        } else if(input.equals("resign")) {
            // Increment move because the next player will win
            moves++;
            resigned = true;
            finished = true;
        } else if(input.equals("draw")) {
            draw += (draw50 / 50) + 1;
            finished = true;
        } else if(input.equals("decline")) {
            draw = 0;
            finished = true;
        } else if(draw > 0) {
            makeMove("decline");
            moves++;
            input = "";
            finished = true;
        }
        
        // Parsing move
        if(selected.equals("Castle")) {
            castle((King) pieces.get("King").get(color).get(0), input);
            finished = true;
            log += String.format("%1$-7s", " " + input);
        }
        if(!selected.equals("Pawn")) {
            move = move.substring(1);
        }
        if(charInRange(move, 0, '1', '8')) {
            rankSpecifier = move.charAt(0) - '0';
            move = move.substring(1);
        } else if(charInRange(move, 0, 'a', 'h') &&
                charInRange(move, 1, 'a', 'h')) {
            fileSpecifier = move.charAt(0);
            move = move.substring(1);
        }
        if(move.charAt(0) == 'x') {
            capturing = true;
            move = move.substring(1);
        } else if(charInRange(move, 1, 'x', 'x')) {
            // pawn capture;
            capturing = true;
            fileSpecifier = move.charAt(0);
            move = move.substring(2);
        }
        // We can ignore check symbols, we will calculate anyway
        if(move.charAt(move.length() - 1) == '+' || move.charAt(move.length() - 1) == '#') {
            move = move.substring(0, move.length() - 1);
        }

        // Promotion
        if(selected.equals("Pawn") && move.length() > 3 &&
                charInRange(move, move.length() - 2, '=', '=')) {
            if(!(move.charAt(move.length() - 3) == '8' || move.charAt(move.length() - 3) == '1')) {
                throw new IllegalArgumentException("Wrong rank to promote");
            }
            String piece = ""+move.charAt(move.length() - 1);
            if(ABBVS.get(piece) != null) {
                promotion = piece;
            }
            move = move.substring(0, 2);
        } else if(selected.equals("Pawn") && (move.charAt(move.length() - 1) == '8' ||
                move.charAt(move.length() - 1) == '1')) {
            throw new IllegalArgumentException("Promotion piece not specified");
        }

        if(!finished && move.length() < 3) {
            // To store legal moves and which pieces can get to them
            Map<Square, List<Piece>> legal = new HashMap<>();
            // Iterate through List of selected pieces
            if(pieces.get(selected).get(color) != null) {
                for(Piece p : pieces.get(selected).get(color)) {
                    p.calcLegal();
                    for(Square s : p.legalSquares) {
                        if(legal.containsKey(s)) {
                            legal.get(s).add(p);
                        } else {
                            legal.put(s, new ArrayList<>(Arrays.asList(p)));
                        }
                    }
                }
            } else {
                throw new IllegalArgumentException("There are no more of those pieces");
            }
            if(legal.containsKey(board.getSquare(move))) {
                // Move is legal
                Piece piece = null;
                if(legal.get(board.getSquare(move)).size() == 1) {
                    // Only one piece can get to this square
                    piece = legal.get(board.getSquare(move)).get(0);
                } else {
                    // Unless wrong specifier, only one piece should apply
                    if(rankSpecifier > 0) {
                        for(Piece p : legal.get(board.getSquare(move))) {
                            if(p.square.rank == rankSpecifier) {
                                piece = p;
                            }
                        }
                    } else if(fileSpecifier > 0) {
                        for(Piece p : legal.get(board.getSquare(move))) {
                            if(p.square.file == fileSpecifier) {
                                piece = p;
                            }
                        }
                    } else {
                        throw new IllegalArgumentException("Piece not specified;" +
                                "multiple pieces can reach that square.");
                    }
                }
                if(piece == null) {
                    throw new IllegalArgumentException("Specified piece not found.");
                }

                // Capturing
                if(board.getSquare(move).piece != null || board.getSquare(move).equals(ep)) {
                    if(!capturing) {
                        throw new IllegalArgumentException("There's a piece there!");
                    }
                    ArrayList<Piece> remove = null;
                    if(board.getSquare(move).equals(ep)) {
                        Square passed = board.getSquare(move).relSquare(0,
                                color.equals(Piece.Color.WHITE) ? -1 : 1);
                        remove = pieces.get(passed.piece.piece).get(Piece.oppColor(color));
                        remove.remove(passed.piece);
                        passed.piece = null;
                    } else {
                        remove = pieces.get(board.getSquare(move).piece.piece)
                                .get(Piece.oppColor(color));
                        remove.remove(board.getSquare(move).piece);
                    }
                    draw50 = -1;
                } else if(capturing) {
                    throw new IllegalArgumentException("There's no piece to capture...");
                }

                // Promotion
                if(piece instanceof Pawn && promotion != null) {
                    if(!(promotion.equals("Q") || promotion.equals("R") ||
                            promotion.equals("N") || promotion.equals("B"))) {
                        throw new IllegalArgumentException("Can't promote to " + promotion);
                    }
                    ArrayList<Piece> pawnList = pieces.get("Pawn").get(color);
                    pawnList.remove(piece);
                    Piece newPiece = constructPiece(ABBVS.get(promotion), color, piece.square);
                    piece.square.piece = newPiece;
                    add(pieces.get(ABBVS.get(promotion)), color, newPiece);
                    piece = newPiece;
                    draw50 = -1;
                }

                // Movement
                board.getSquare(move).piece = piece;
                board.getSquare(piece.square.toString()).piece = null;
                piece.square = board.getSquare(move);
                boolean newEp = false;

                // Special movement
                if(piece instanceof Pawn) {
                    if(((Pawn) piece).canDouble) {
                        if(piece.color.equals(Piece.Color.WHITE) && piece.square.rank == 4 ||
                                piece.color.equals(Piece.Color.BLACK) && piece.square.rank == 5) {
                            // Doubled
                            ep = piece.square.relSquare(0,
                                    piece.color.equals(Piece.Color.WHITE) ? -1 : 1);
                            newEp = true;
                        }
                        ((Pawn) piece).canDouble = false;
                    }
                    draw50 = -1;
                } else if(piece instanceof King) {
                    piece.calcLegal();
                    if(((King) piece).canCastle) {
                        ((King) piece).canCastle = false;
                    }
                } else if(piece instanceof Rook && ((Rook) piece).canCastle) {
                    ((Rook) piece).canCastle = false;
                }
                if(!newEp) {
                    ep = null;
                }
            } else {
                throw new IllegalArgumentException("No piece can legally reach that square.");
            }
        }
        if(color.equals(Piece.Color.WHITE)) {
            log += "\n" + ((moves + 2) / 2) + ".";
        }
        log += String.format("%1$-7s", " " + input);
        draw50++;
        moves++;
    }

    /**
     * Helper method to use Scanner.
     * @param input Scanner object to fetch input
     */
    public void makeMove(Scanner input) {
        makeMove(input.nextLine());
    }

    /**
     * Checks if the game is over through checkmate, resignation, or a draw condition.
     * @return true if the game is over
     */
    public boolean isGameOver() {
        if(resigned || draw > 1) {
            return true;
        }
        for(int i = 0; i < 2; i++) {
            Piece king = pieces.get("King").get(C[i]).get(0);
            king.calcLegal();
            if(king.square.isAttacked(Piece.oppColor(king.color)) &&
                    king.legalSquares.size() == 0) {
                for(String type : pieces.keySet()) {
                    if(pieces.get(type).get(C[i]) != null) {
                        for(Piece piece : pieces.get(type).get(C[i])) {
                            piece.calcLegal();
                            if(piece.legalSquares.size() > 0) {
                                return false;
                            }
                        }
                    }
                }
                String last = log.substring(log.lastIndexOf(" ") + 1) + "#";
                log = log.substring(0, (log.length() - last.length()) - 1) + last;
                return true;
            } else {
                // Stalemate
                int legalMoves = 0;
                for(String type : pieces.keySet()) {
                    if(pieces.get(type).get(C[i]) != null) {
                        for(Piece piece : pieces.get(type).get(C[i])) {
                            piece.calcLegal();
                            if(piece.legalSquares.size() > 0) {
                                legalMoves += piece.legalSquares.size();
                            }
                        }
                    }
                }
                if(legalMoves == 0) {
                    draw = 2;
                    return true;
                }
                // Dead position
                boolean hasPieces = false;
                for(String type : pieces.keySet()) {
                    if(pieces.get(type).get(C[i]) == null ||
                            pieces.get(type).get(C[i]).size() > 0) {
                        hasPieces = true;
                    }
                }
                if(!hasPieces) {
                    return true;
                }
                // TODO: Extend Dead Position draw rule
                // TODO: Threefold repitition
            }
        }
        return false;
    }

    /**
     * Returns the winner of the game.
     * @return 1 if White won, 2 if Black won, and 0 if there was a draw
     */
    public int getWinner() {
        System.out.println(log);
        return draw > 1 ? 0 : moves % 2 == 0 ? 2 : 1;
    }

    /**
     * Formats the current board state.
     * @return a String representation of the Chess board
     */
    public String toString() {
        String out = "\n |‾‾‾‾‾|‾‾‾‾‾|‾‾‾‾‾|‾‾‾‾‾|‾‾‾‾‾|‾‾‾‾‾|‾‾‾‾‾|‾‾‾‾‾|\n";
        for(Square[] rank : board.board) {
            out += (rank[0].rank != 8 ?
                    " |     |     |     |     |     |     |     |     |\n" : "") +
                    rank[0].rank + "|";
            for(Square square : rank) {
                out += "  " + (square.piece != null ? square.piece : " ") + "  |";
            }
            out += "\n |_____|_____|_____|_____|_____|_____|_____|_____|\n";
        }
        out += "    A     B     C     D     E     F     G     H";
        return out;
    }

    /**
     * Retrieves which player's turn it is.
     * @return 1 for White, 2 for Black
     */
    public int getNextPlayer() {
        if(isGameOver()) { return -1; }
        return moves % 2 + 1;
    }

    /**
     * Retrieves the instructions for playing Chess.
     * @return a String containing the instructions
     */
    public String instructions() {
        String out = "Chess pieces are divided into two sets, referred to as white and black.\n";
        out += "The players of the sets are referred to as White (P1) and Black (P2),\n";
        out += "respectively. On the board, the outlined pieces are white and the solid color\n";
        out += "pieces are black. Each set consists of sixteen pieces: one king, one queen, two\n";
        out += "rooks, two bishops, two knights, and eight pawns. The game is played on a square\n";
        out += "board of eight rows (called ranks) and eight columns (called files).\n";
        out += "\nOn White's first rank, from left to right, the pieces are placed as follows:\n";
        out += "rook, knight, bishop, queen, king, bishop, knight, rook.\n";
        out += "Eight pawns are placed on the second rank. Black's position mirrors White's,\n";
        out += "with an equivalent piece on the same file.";
        out += "\nWhite moves first, after which players alternate turns, moving one piece per\n";
        out += "turn. A piece is moved to either an unoccupied square or one occupied by an\n";
        out += "opponent's piece, which is captured and removed from play. All pieces capture\n";
        out += "by moving to the square that the opponent's piece occupies.\n";
        out += "Moving is compulsory; a player may not skip a turn, even when having to move\n";
        out += "is detrimental.\n";
        out += "\nEach piece has its own way of moving. In the diagrams, crosses mark the\n";
        out += "squares to which the piece can move if there are no intervening piece(s) of\n";
        out += "either color (except the knight, which leaps over any intervening pieces).\n";
        out += "All pieces except the pawn can capture an enemy piece if it is on a square to\n";
        out += "which they could move if the square were unoccupied.\n\n";
        out += " |‾‾‾‾‾|‾‾‾‾‾|‾‾‾‾‾|‾‾‾‾‾|‾‾‾‾‾|‾‾‾‾‾|‾‾‾‾‾|‾‾‾‾‾|\n";
        out += "8|     |     |     |     |     |     |     |     |\n";
        out += " |_____|_____|_____|_____|_____|_____|_____|_____|\n";
        out += " |     |     |     |     |     |     |     |     |\n";
        out += "7|     |     |     |     |     |     |     |     |\n";
        out += " |_____|_____|_____|_____|_____|_____|_____|_____|\n";
        out += " |     |     |     |     |     |     |     |     |\n";
        out += "6|     |     |     |     |  X  |  X  |  X  |     |\n";
        out += " |_____|_____|_____|_____|_____|_____|_____|_____|\n";
        out += " |     |     |     |     |     |     |     |     |\n";
        out += "5|     |     |     |     |  X  |  ♔  |  X  |     |\n";
        out += " |_____|_____|_____|_____|_____|_____|_____|_____|\n";
        out += " |     |     |     |     |     |     |     |     |\n";
        out += "4|     |     |     |     |  X  |  X  |  X  |     |\n";
        out += " |_____|_____|_____|_____|_____|_____|_____|_____|\n";
        out += " |     |     |     |     |     |     |     |     |\n";
        out += "3|     |     |     |     |     |     |     |     |\n";
        out += " |_____|_____|_____|_____|_____|_____|_____|_____|\n";
        out += " |     |     |     |     |     |     |     |     |\n";
        out += "2|     |     |     |     |     |     |     |     |\n";
        out += " |_____|_____|_____|_____|_____|_____|_____|_____|\n";
        out += " |     |     |     |     |     |     |     |     |\n";
        out += "1|     |     |     |     |     |     |     |     |\n";
        out += " |_____|_____|_____|_____|_____|_____|_____|_____|\n";
        out += "    A     B     C     D     E     F     G     H\n";
        out += "-The King moves one square in any direction. There is also a special move\n";
        out += "called castling that involves moving the king and a rook. The king is the most\n";
        out += "valuable piece—attacks on the king must be immediately countered, and if this\n";
        out += "is impossible, the game is immediately lost (see Check and Checkmate).\n\n";
        out += " |‾‾‾‾‾|‾‾‾‾‾|‾‾‾‾‾|‾‾‾‾‾|‾‾‾‾‾|‾‾‾‾‾|‾‾‾‾‾|‾‾‾‾‾|\n";
        out += "8|     |     |     |  X  |     |     |     |     |\n";
        out += " |_____|_____|_____|_____|_____|_____|_____|_____|\n";
        out += " |     |     |     |     |     |     |     |     |\n";
        out += "7|     |     |     |  X  |     |     |     |     |\n";
        out += " |_____|_____|_____|_____|_____|_____|_____|_____|\n";
        out += " |     |     |     |     |     |     |     |     |\n";
        out += "6|     |     |     |  X  |     |     |     |     |\n";
        out += " |_____|_____|_____|_____|_____|_____|_____|_____|\n";
        out += " |     |     |     |     |     |     |     |     |\n";
        out += "5|  X  |  X  |  X  |  ♖  |  X  |  X  |  X  |  X  |\n";
        out += " |_____|_____|_____|_____|_____|_____|_____|_____|\n";
        out += " |     |     |     |     |     |     |     |     |\n";
        out += "4|     |     |     |  X  |     |     |     |     |\n";
        out += " |_____|_____|_____|_____|_____|_____|_____|_____|\n";
        out += " |     |     |     |     |     |     |     |     |\n";
        out += "3|     |     |     |  X  |     |     |     |     |\n";
        out += " |_____|_____|_____|_____|_____|_____|_____|_____|\n";
        out += " |     |     |     |     |     |     |     |     |\n";
        out += "2|     |     |     |  X  |     |     |     |     |\n";
        out += " |_____|_____|_____|_____|_____|_____|_____|_____|\n";
        out += " |     |     |     |     |     |     |     |     |\n";
        out += "1|     |     |     |  X  |     |     |     |     |\n";
        out += " |_____|_____|_____|_____|_____|_____|_____|_____|\n";
        out += "    A     B     C     D     E     F     G     H\n";
        out += "-A rook can move any number of squares along a rank or file, but cannot leap\n";
        out += "over other pieces. Along with the king, a rook is involved during the king's\n";
        out += "castling move.\n\n";
        out += " |‾‾‾‾‾|‾‾‾‾‾|‾‾‾‾‾|‾‾‾‾‾|‾‾‾‾‾|‾‾‾‾‾|‾‾‾‾‾|‾‾‾‾‾|\n";
        out += "8|     |     |     |     |     |     |     |  X  |\n";
        out += " |_____|_____|_____|_____|_____|_____|_____|_____|\n";
        out += " |     |     |     |     |     |     |     |     |\n";
        out += "7|  X  |     |     |     |     |     |  X  |     |\n";
        out += " |_____|_____|_____|_____|_____|_____|_____|_____|\n";
        out += " |     |     |     |     |     |     |     |     |\n";
        out += "6|     |  X  |     |     |     |  X  |     |     |\n";
        out += " |_____|_____|_____|_____|_____|_____|_____|_____|\n";
        out += " |     |     |     |     |     |     |     |     |\n";
        out += "5|     |     |  X  |     |  X  |     |     |     |\n";
        out += " |_____|_____|_____|_____|_____|_____|_____|_____|\n";
        out += " |     |     |     |     |     |     |     |     |\n";
        out += "4|     |     |     |  ♗  |     |     |     |     |\n";
        out += " |_____|_____|_____|_____|_____|_____|_____|_____|\n";
        out += " |     |     |     |     |     |     |     |     |\n";
        out += "3|     |     |  X  |     |  X  |     |     |     |\n";
        out += " |_____|_____|_____|_____|_____|_____|_____|_____|\n";
        out += " |     |     |     |     |     |     |     |     |\n";
        out += "2|     |  X  |     |     |     |  X  |     |     |\n";
        out += " |_____|_____|_____|_____|_____|_____|_____|_____|\n";
        out += " |     |     |     |     |     |     |     |     |\n";
        out += "1|  X  |     |     |     |     |     |  X  |     |\n";
        out += " |_____|_____|_____|_____|_____|_____|_____|_____|\n";
        out += "    A     B     C     D     E     F     G     H\n";
        out += "-A bishop can move any number of squares diagonally, but cannot leap over other\n";
        out += "pieces.\n\n";
        out += " |‾‾‾‾‾|‾‾‾‾‾|‾‾‾‾‾|‾‾‾‾‾|‾‾‾‾‾|‾‾‾‾‾|‾‾‾‾‾|‾‾‾‾‾|\n";
        out += "8|     |     |     |  X  |     |     |     |  X  |\n";
        out += " |_____|_____|_____|_____|_____|_____|_____|_____|\n";
        out += " |     |     |     |     |     |     |     |     |\n";
        out += "7|  X  |     |     |  X  |     |     |  X  |     |\n";
        out += " |_____|_____|_____|_____|_____|_____|_____|_____|\n";
        out += " |     |     |     |     |     |     |     |     |\n";
        out += "6|     |  X  |     |  X  |     |  X  |     |     |\n";
        out += " |_____|_____|_____|_____|_____|_____|_____|_____|\n";
        out += " |     |     |     |     |     |     |     |     |\n";
        out += "5|     |     |  X  |  X  |  X  |     |     |     |\n";
        out += " |_____|_____|_____|_____|_____|_____|_____|_____|\n";
        out += " |     |     |     |     |     |     |     |     |\n";
        out += "4|  X  |  X  |  X  |  ♕  |  X  |  X  |  X  |  X  |\n";
        out += " |_____|_____|_____|_____|_____|_____|_____|_____|\n";
        out += " |     |     |     |     |     |     |     |     |\n";
        out += "3|     |     |  X  |  X  |  X  |     |     |     |\n";
        out += " |_____|_____|_____|_____|_____|_____|_____|_____|\n";
        out += " |     |     |     |     |     |     |     |     |\n";
        out += "2|     |  X  |     |  X  |     |  X  |     |     |\n";
        out += " |_____|_____|_____|_____|_____|_____|_____|_____|\n";
        out += " |     |     |     |     |     |     |     |     |\n";
        out += "1|  X  |     |     |  X  |     |     |  X  |     |\n";
        out += " |_____|_____|_____|_____|_____|_____|_____|_____|\n";
        out += "    A     B     C     D     E     F     G     H\n";
        out += "-A queen combines the power of a rook and bishop and can move any number of\n";
        out += "squares along a rank, file, or diagonal, but cannot leap over other pieces.\n\n";
        out += " |‾‾‾‾‾|‾‾‾‾‾|‾‾‾‾‾|‾‾‾‾‾|‾‾‾‾‾|‾‾‾‾‾|‾‾‾‾‾|‾‾‾‾‾|\n";
        out += "8|     |     |     |     |     |     |     |     |\n";
        out += " |_____|_____|_____|_____|_____|_____|_____|_____|\n";
        out += " |     |     |     |     |     |     |     |     |\n";
        out += "7|     |     |  X  |     |  X  |     |     |     |\n";
        out += " |_____|_____|_____|_____|_____|_____|_____|_____|\n";
        out += " |     |     |     |     |     |     |     |     |\n";
        out += "6|     |  X  |     |     |     |  X  |     |     |\n";
        out += " |_____|_____|_____|_____|_____|_____|_____|_____|\n";
        out += " |     |     |     |     |     |     |     |     |\n";
        out += "5|     |     |     |  ♘  |     |     |     |     |\n";
        out += " |_____|_____|_____|_____|_____|_____|_____|_____|\n";
        out += " |     |     |     |     |     |     |     |     |\n";
        out += "4|     |  X  |     |     |     |  X  |     |     |\n";
        out += " |_____|_____|_____|_____|_____|_____|_____|_____|\n";
        out += " |     |     |     |     |     |     |     |     |\n";
        out += "3|     |     |  X  |     |  X  |     |     |     |\n";
        out += " |_____|_____|_____|_____|_____|_____|_____|_____|\n";
        out += " |     |     |     |     |     |     |     |     |\n";
        out += "2|     |     |     |     |     |     |     |     |\n";
        out += " |_____|_____|_____|_____|_____|_____|_____|_____|\n";
        out += " |     |     |     |     |     |     |     |     |\n";
        out += "1|     |     |     |     |     |     |     |     |\n";
        out += " |_____|_____|_____|_____|_____|_____|_____|_____|\n";
        out += "    A     B     C     D     E     F     G     H\n";
        out += "-A knight moves to any of the closest squares that are not on the same rank,\n";
        out += "file, or diagonal. (Thus the move forms an \"L\"-shape: two squares vertically\n";
        out += "and one square horizontally, or two squares horizontally and one square\n";
        out += "vertically.) The knight is the only piece that can leap over other pieces.\n\n";
        out += " |‾‾‾‾‾|‾‾‾‾‾|‾‾‾‾‾|‾‾‾‾‾|‾‾‾‾‾|‾‾‾‾‾|‾‾‾‾‾|‾‾‾‾‾|\n";
        out += "8|     |     |     |     |     |     |     |     |\n";
        out += " |_____|_____|_____|_____|_____|_____|_____|_____|\n";
        out += " |     |     |     |     |     |     |     |     |\n";
        out += "7|     |     |     |     |     |     |     |     |\n";
        out += " |_____|_____|_____|_____|_____|_____|_____|_____|\n";
        out += " |     |     |     |     |     |     |     |     |\n";
        out += "6|     |     |     |     |     |     |     |     |\n";
        out += " |_____|_____|_____|_____|_____|_____|_____|_____|\n";
        out += " |     |     |     |     |     |     |     |     |\n";
        out += "5|     |  X  |  0  |  X  |     |     |     |     |\n";
        out += " |_____|_____|_____|_____|_____|_____|_____|_____|\n";
        out += " |     |     |     |     |     |     |     |     |\n";
        out += "4|     |     |  ♙  |     |     |  0  |     |     |\n";
        out += " |_____|_____|_____|_____|_____|_____|_____|_____|\n";
        out += " |     |     |     |     |     |     |     |     |\n";
        out += "3|     |     |     |     |  X  |  0  |  X  |     |\n";
        out += " |_____|_____|_____|_____|_____|_____|_____|_____|\n";
        out += " |     |     |     |     |     |     |     |     |\n";
        out += "2|     |     |     |     |     |  ♙  |     |     |\n";
        out += " |_____|_____|_____|_____|_____|_____|_____|_____|\n";
        out += " |     |     |     |     |     |     |     |     |\n";
        out += "1|     |     |     |     |     |     |     |     |\n";
        out += " |_____|_____|_____|_____|_____|_____|_____|_____|\n";
        out += "    A     B     C     D     E     F     G     H\n";
        out += "A pawn can move forward to the unoccupied square immediately in front of it on\n";
        out += "the same file, or on its first move it can advance two squares along the same\n";
        out += "file, provided both squares are unoccupied (0 in the diagram). A pawn can\n";
        out += "capture an opponent's piece on a square diagonally in front of it by moving to\n";
        out += "that square (X). It cannot capture a piece while advancing along the same file.\n";
        out += "A pawn has two special moves: the en passant capture and promotion.\n\n";
        out += "When a king is under immediate attack, it is said to be in check. A move in\n";
        out += "response to a check is legal only if it results in a position where the king is\n";
        out += "no longer in check. There are three ways to counter a check:\n";
        out += "-Capture the checking piece\n";
        out += "-Interpose a piece between the checking piece and the king (which is possible\n";
        out += " only if the attacking piece is a queen, rook, or bishop and there is a square\n";
        out += " between it and the king).\n";
        out += "-Move the king to a square where it is not under attack.\n";
        out += "(Castling is not a permissible response to a check.)\n";
        out += "The object of the game is to checkmate the opponent; this occurs when the\n";
        out += "opponent's king is in check, and there is no legal way to get it out of check.\n";
        out += "It is never legal for a player to make a move that puts or leaves the player's\n";
        out += "own king in check.\n\n";
        out += "Once per game, each king can make a move known as castling. Castling consists\n";
        out += "of moving the king two squares toward a rook of the same color on the same\n";
        out += "rank, and then placing the rook on the square that the king crossed.\n";
        out += "Castling is legal if the following conditions are met:\n";
        out += "-Neither the king nor the rook has previously moved during the game.\n";
        out += "-There are no pieces between the king and the rook.\n";
        out += "The king is not in check and does not pass through or finish on a square\n";
        out += " attacked by an enemy piece.\n";
        out += "(Castling is still permitted if the rook is under attack, or if the rook\n";
        out += "crosses an attacked square.)\n\n";
        out += "When a pawn makes a two-step advance from its starting position and there\n";
        out += "is an opponent's pawn on a square next to the destination square on an\n";
        out += "adjacent file, then the opponent's pawn can capture it\n";
        out += "en passant (\"in passing\"), moving to the square the pawn passed over.\n";
        out += "This can be done only on the turn immediately following the enemy pawn's\n";
        out += "two-square advance; otherwise, the right to do so is forfeited.\n\n";
        out += "When a pawn advances to its eighth rank, as part of the move, it is promoted\n";
        out += "and must be exchanged for the player's choice of queen, rook, bishop, or knight\n";
        out += "of the same color. Usually, the pawn is chosen to be promoted to a queen, but\n";
        out += "in some cases, another piece is chosen; this is called underpromotion.\n";
        out += "There is no restriction on the piece promoted to, so it is possible to have\n";
        out += "more pieces of the same type than at the start of the game (e.g., two queens).\n";
        out += "\nA game can be won in the following ways:\n";
        out += "-Checkmate: The king is in check and the player has no legal move.\n";
        out += "-Resignation: A player may resign, conceding the game to the opponent.\n";
        out += "\nThere are several ways a game can end in a draw:\n";
        out += "-Stalemate: If the player to move has no legal move, but is not in check,\n";
        out += " the position is a stalemate, and the game is drawn.\n";
        out += "-Dead position: If neither player is able to checkmate the other by any legal\n";
        out += " sequence of moves, the game is drawn. For example, if only the kings are on\n";
        out += " the board, all other pieces having been captured, checkmate is impossible,\n";
        out += " and the game is drawn by this rule.\n";
        out += "-Draw by agreement: In tournament chess, draws are most commonly reached by\n";
        out += " mutual agreement between the players.";
        out += "-Fifty-move rule: If during the previous 50 moves no pawn has been moved and no\n";
        out += " capture has been made, either player can claim a draw.\n\n";
        out += "This chess engine takes Algebraic Notation as its input.\n";
        out += "In this system, each square is uniquely identified by a set of coordinates,\n";
        out += "a-h for the files followed by 1-8 for the ranks. The usual format is:\n";
        out += "\tinitial of the piece moved - file of destination square - rank of square\n\n";
        out += "The pieces are identified by their initials. In English, these are K (king),\n";
        out += "Q (queen), R (rook), B (bishop), and N (knight; N is used to avoid confusion\n";
        out += "with king). For example, Qg5 means \"queen moves to the g-file, 5th rank\"\n\n";
        out += "To resolve ambiguities, an additional letter or number is added to indicate\n";
        out += "the file or rank from which the piece moved (e.g. Ngf3 means \"knight from\n";
        out += "the g-file moves to the square f3\"; R1e2 means \"rook on the first rank moves\n";
        out += "to e2\"). For pawns, no letter initial is used; so e4 means \"pawn moves to\n";
        out += "the square e4\".\n";
        out += "If the piece makes a capture, \"x\" is usually inserted before the destination\n";
        out += "square. Thus Bxf3 means \"bishop captures on f3\". When a pawn makes a capture,\n";
        out += "the file from which the pawn departed is used to identify the pawn making the\n";
        out += "capture, for example, exd5 (pawn on the e-file captures the piece on d5).\n\n";
        out += "If a pawn moves to its last rank, achieving promotion, the piece chosen is\n";
        out += "indicated after the move (e.g. e8=Q)\n";
        out += "Castling is indicated by the special notations O-O for kingside castling and\n";
        out += "O-O-O for queenside castling.\n\n";
        out += "Have fun!\n";
        return out;
    }

    /**
     * A helper method that will return every legal move for the specified color.
     * @param color the Color to retrieve legal moves from
     * @return The list of legal moves
     */
    public List<String> getAllLegalMoves(Piece.Color color) {
        List<String> moves = new ArrayList<>();
        for(String key : pieces.keySet()) {
            if(pieces.get(key) != null && pieces.get(key).get(color) != null) {
                for(Piece p : pieces.get(key).get(color)) {
                    p.calcLegal();
                    moves.add(String.format("%s@%s: %s", p, p.square, p.legalSquares));
                }
            }
        }
        return moves;
    }

    /**
     * A helper method that returns the color of the current player.
     * @return The color of the current player
     */
    public Piece.Color getCurrColor() {
        return C[getNextPlayer() - 1];
    }

    /**
     * A private helper method to streamline creating the different Chess pieces.
     * @param type The name of the piece to create
     * @param color The color of the piece
     * @param square The square the piece is on
     * @return The constructed piece
     */
    private Piece constructPiece(String type, Piece.Color color, Square square) {
        if(type.equals("King")) {
            return new King(color, square);
        } else if(type.equals("Queen")) {
            return new Queen(color, square);
        } else if(type.equals("Rook")) {
            return new Rook(color, square);
        } else if(type.equals("Knight")) {
            return new Knight(color, square);
        } else if(type.equals("Bishop")) {
            return new Bishop(color, square);
        } else if(type.equals("Pawn")) {
            return new Pawn(color, square);
        }
        return null;
    }

    /**
     * A private helper method to streamline adding Pieces to the specified map.
     * @param map The map to add Pieces to
     * @param color The color of the Piece
     * @param piece The piece
     */
    private void add(Map<Piece.Color, ArrayList<Piece>> map, Piece.Color color, Piece piece) {
        if(map.containsKey(color)) {
            map.get(color).add(piece);
        } else {
            map.put(color, new ArrayList<Piece>(Arrays.asList(piece)));
        }
    }

    /**
     * A private helper method to handle castling.
     * @param king The King to castle
     * @param move The move in Algebraic Notation (e.g. O-O)
     * @throws IllegalArgumentException If castling is impossible in the current board state
     */
    private void castle(King king, String move) {
        Rook rook = (Rook) pieces.get("Rook").get(king.color).get(move.equals("O-O") ? 1 : 0);
        int dir = move.equals("O-O") ? 1 : -1;
        if(king.canCastle && rook.canCastle &&
                !king.square.isAttacked(Piece.oppColor(king.color)) &&
                king.square.relSquare(dir, 0).piece == null &&
                !king.square.relSquare(dir, 0).isAttacked(Piece.oppColor(king.color)) &&
                king.square.relSquare(dir * 2, 0).piece == null &&
                !king.square.relSquare(dir * 2, 0).isAttacked(Piece.oppColor(king.color))) {
            Square kDest = king.square.relSquare(dir * 2, 0);
            Square rDest = king.square.relSquare(dir, 0);
            kDest.piece = king;
            king.square.piece = null;
            king.square = kDest;
            king.canCastle = false;
            rDest.piece = rook;
            rook.square.piece = null;
            rook.square = rDest;
            rook.canCastle = false;
        } else {
            throw new IllegalArgumentException("Can't castle");
        }
    }

    /**
     * A private helper method to check if a char in a String is in the range of two other chars
     * @param str The String to check
     * @param idx The index of the character in the String
     * @param a The lower character bound
     * @param b The upper character bound
     * @return true if the character is in range, false otherwise
     */
    private boolean charInRange(String str, int idx, char a, char b) {
        if(str.length() > idx) {
            return a <= str.charAt(idx) && str.charAt(idx) <= b;
        }
        return false;
    }

    // A class to represent the King
    private class King extends Piece {
        private boolean canCastle;

        /**
         * Constructs a new King.
         * @param color Color of the King
         * @param square Square the King is on
         */
        public King(Piece.Color color, Square square) {
            super(color, "King", square);
            canCastle = true;
        }

        /**
         * Calculates the King's current legal moves.
         */
        public void calcLegal() {
            legalSquares.clear();
            square.piece = null;
            for(int vert = -1; vert < 2; vert++) {
                for(int hori = -1; hori < 2; hori++) {
                    addIfLegal(hori, vert);
                }
            }
            ArrayList<Square> temp = new ArrayList<>();
            for(Square s : legalSquares) {
                if(!s.equals(square) && !s.isAttacked(Piece.oppColor(color))) {
                    temp.add(s);
                }
            }
            square.piece = this;
            legalSquares = temp;
        }
    }

    // A class to represent the Queen
    private class Queen extends Piece {
        /**
         * Constructs a new Queen.
         * @param color Color of the Queen
         * @param square Square the Queen is on
         */
        public Queen(Piece.Color color, Square square) {
            super(color, "Queen", square);
        }

        /**
         * Calculates the Queen's current legal moves.
         */
        public void calcLegal() {
            legalSquares.clear();
            int i;
            for(int vert = -1; vert < 2; vert++) {
                for(int hori = -1; hori < 2; hori++) {
                    i = 1;
                    while(addIfLegal(hori * i, vert * i++));
                }
            }
        }
    }

    // A class to represent the Rook
    private class Rook extends Piece {
        private boolean canCastle;

        /**
         * Constructs a new Rook.
         * @param color Color of the Rook
         * @param square Square the Rook is on
         */
        public Rook(Piece.Color color, Square square) {
            super(color, "Rook", square);
            canCastle = true;
        }

        /**
         * Calculates the Rook's current legal moves.
         */
        public void calcLegal() {
            legalSquares.clear();
            int i;
            for(int dir = -1; dir < 2; dir += 2) {
                i = 1;
                while(addIfLegal(dir * i++, 0));
                i = 1;
                while(addIfLegal(0, dir * i++));
            }
        }
    }

    // A class to represent the Knight
    private class Knight extends Piece {
        /**
         * Constructs a new Knight.
         * @param color Color of the Knight
         * @param square Square the Knight is on
         */
        public Knight(Piece.Color color, Square square) {
            super(color, "Knight", square);
        }

        /**
         * Calculates the Knight's current legal moves.
         */
        public void calcLegal() {
            legalSquares.clear();
            for(int primary = -2; primary < 3; primary += 4) {
                for(int offset = -1; offset < 2; offset += 2) {
                    addIfLegal(offset, primary);
                    addIfLegal(primary, offset);
                }
            }
        }
    }

    // A class to represent the Bishop
    private class Bishop extends Piece {
        /**
         * Constructs a new Bishop.
         * @param color Color of the Bishop
         * @param square Square the Bishop is on
         */
        public Bishop(Piece.Color color, Square square) {
            super(color, "Bishop", square);
        }

        /**
         * Calculates the Bishop's current legal moves.
         */
        public void calcLegal() {
            legalSquares.clear();
            int i;
            for(int vert = -1; vert < 2; vert += 2) {
                for(int hori = -1; hori < 2; hori += 2) {
                    i = 1;
                    while(addIfLegal(hori * i, vert * i++));
                }
            }
        }
    }

    // A class to represent the Pawn
    private class Pawn extends Piece {
        private boolean canDouble;

        /**
         * Constructs a new Pawn.
         * @param color Color of the Pawn
         * @param square Square the Pawn is on
         */
        public Pawn(Piece.Color color, Square square) {
            super(color, "Pawn", square);
            canDouble = true;
        }

        /**
         * Calculates the Pawn's current legal moves.
         */
        public void calcLegal() {
            legalSquares.clear();
            int colorDir = color.equals(Piece.Color.WHITE) ? 1 : -1;
            addIfLegal(0, colorDir);
            if(canDouble) { addIfLegal(0, 2 * colorDir); }
            addIfCapturable(-1, colorDir, color);
            addIfCapturable(1, colorDir, color);
        }
    }

    // An abstract class to encapsulate all Pieces
    private abstract class Piece {
        private enum Color {
            WHITE,
            BLACK
        }
        protected static final Map<Color, Map<String, String>> ICONS = Map.of(
                Color.WHITE, Map.of("King","♔","Queen","♕","Rook","♖",
                        "Knight","♘","Bishop","♗","Pawn","♙"),
                Color.BLACK, Map.of("King","♚","Queen","♛","Rook","♜",
                        "Knight","♞","Bishop","♝","Pawn","♟"));
        protected List<Square> legalSquares;
        protected Square square;
        protected Color color;
        private String piece;

        /**
         * Constructs a new Piece.
         * @param color Color of the Piece
         * @param piece Name of the Piece
         * @param square Square the Piece is on
         */
        public Piece(Color color, String piece, Square square) {
            this.legalSquares = new ArrayList<>();
            this.color = color;
            this.piece = piece;
            this.square = square;
        }

        /**
         * A helper method to simply get the opposite color of the input.
         * @param color Input color
         * @return the opposite color
         */
        public static Color oppColor(Color color) {
            return color.equals(Color.WHITE) ? Color.BLACK : Color.WHITE;
        }

        /**
         * A helper method to add the square defined by the offsets to the list of legal moves
         * if the move is legal.
         * @param fileOffset Horizontal offset from the piece's current square
         * @param rankOffset Vertical offset from the piece's current square
         * @return true if the square was added or
         *         if the caller can continue searching for legal squares
         */
        public boolean addIfLegal(int fileOffset, int rankOffset) {
            Square tSqr = square.relSquare(fileOffset, rankOffset);
            if(tSqr != null) {
                // Check if square can be moved to
                if(tSqr.piece == null || tSqr.piece.color.equals(oppColor(color))) {
                    // Temporarily make the move
                    Piece temp = tSqr.piece;
                    tSqr.piece = this;
                    square = tSqr;
                    tSqr.relSquare(-fileOffset, -rankOffset).piece = null;
                    // Verify this move will not put/keep King in check
                    if(pieces.get("King").get(color).get(0).square.isAttacked(oppColor(color))) {
                        // Restore game state
                        tSqr.piece = temp;
                        square = tSqr.relSquare(-fileOffset, -rankOffset);
                        square.piece = this;
                        // King is still in check by this move, keep searching
                        return true;
                    }
                    // Restore game state
                    tSqr.piece = temp;
                    square = tSqr.relSquare(-fileOffset, -rankOffset);
                    square.piece = this;
                    // Add move
                    legalSquares.add(tSqr);
                    return tSqr.piece == null;
                }
            }
            // Stop checking
            return false;
        }

        /**
         * A helper method to add the square defined by the offests to the list of legal moves
         * if the move results in a capture.
         * @param fileOffset Horizontal offset from the piece's current square
         * @param rankOffset Vertical offset from the piece's current square
         * @param color Color of the piece
         * @return true if the square was added or
         *         if the caller can continue searching for legal squares
         */
        public boolean addIfCapturable(int fileOffset, int rankOffset, Color color) {
            char tFile = (char)(square.file + fileOffset);
            int tRank = square.rank + rankOffset;
            if(Board.isLegal(tFile, tRank) &&
                    (square.relSquare(fileOffset, rankOffset).piece != null ||
                    square.relSquare(fileOffset, rankOffset).equals(ep))) {
                return addIfLegal(fileOffset, rankOffset);
            }
            return false;
        }

        /**
         * A method to check if two pieces are the same.
         * @return true if the pieces are the same
         */
        public boolean equals(Object obj) {
            if(obj instanceof Piece) {
                Piece o = (Piece) obj;
                return (this.piece.equals(o.piece) && this.color.equals(o.color) &&
                        this.square.equals(o.square));
            }
            return false;
        }

        /**
         * Retrives a representation of the piece as an icon
         * @return the icon of this piece
         */
        public String toString() {
            return ICONS.get(color).get(piece);
        }

        /**
         * Calculates the piece's current legal moves.
         */
        public abstract void calcLegal();
    }

    // A class to represent the Chess board
    private class Board {
        private Square[][] board;

        public Board() {
            board = new Square[8][8];
            for(int i = 0; i < 8; i++) {
                for(int j = 0; j < 8; j++) {
                    board[i][j] = new Square(this, (char)('a' + j), 8 - i);
                }
            }
        }

        /**
         * Retrieves the square on the Chess board with the specified coordinates.
         * @param file The file of the square to retrieve
         * @param rank The rank of the square to retrieve
         * @return the Square if found, null otherwise
         */
        public Square getSquare(char file, int rank) {
            if(isLegal(file, rank)) {
                return board[8-rank][file-'a'];
            }
            return null;
        }

        /**
         * Retrieves the square on the Chess board with the specified coordinates.
         * @param in The coordinate of the square to retrieve
         * @return the Square if found, null otherwise
         */
        public Square getSquare(String in) {
            return getSquare(in.charAt(0), Integer.parseInt(in.substring(1)));
        }

        /**
         * A helper method to check if a coordinate is legal
         * @param file The file to theck
         * @param rank The rank to check
         * @return true if the coordinate is legal
         */
        public static boolean isLegal(char file, int rank) {
            return 'a' <= file && file <= 'h' && 1 <= rank && rank <= 8;
        }
    }

    // A class to represent a square on the Chess board
    private class Square {
        private Board board;
        private char file;
        private int rank;
        private Piece piece;

        /**
         * Constructs a new Square.
         * @param file The file (horizontal coordinate) of this square
         * @param rank The rank (vertical coordinate) of this square
         */
        public Square(Board board, char file, int rank) {
            this.board = board;
            this.file = file;
            this.rank = rank;
        }

        /**
         * Retrieves the square on the Chess board with relative coordinates.
         * @param fileOffset The horizontal offset of the square to retrieve
         * @param rankOffset The vertical offest of the square to retrieve
         * @return the Square if found, null otherwise
         */
        public Square relSquare(int fileOffset, int rankOffset) {
            char tFile = (char)(file + fileOffset);
            int tRank = rank + rankOffset;
            return board.getSquare(tFile, tRank);
        }

        /**
         * A helper method to check if a piece of specified color
         * could move to this square.
         * @param color Color of pieces to check
         * @return true if this square is in the legal squares of the specified color
         */
        public boolean isAttacked(Piece.Color color) {
            Square square;
            // King?
            // Check in any direction if a King is there
            // If there is, check if this square is protected by a friendly piece
            for(int vert = -1; vert < 2; vert++) {
                for(int hori = -1; hori < 2; hori++) {
                    square = relSquare(hori, vert);
                    if(square != null && square.piece instanceof King &&
                            square.piece.color.equals(color)){
                        return true;
                    }
                }
            }
            // Queen/Rook/Bishop?
            int i;
            for(int vert = -1; vert < 2; vert++) {
                for(int hori = -1; hori < 2; hori++) {
                    i = 1;
                    square = relSquare(hori * i, vert * i);
                    while(square != null && !square.equals(this) && square.piece == null) {
                        square = relSquare(hori * ++i, vert * i);
                    }
                    if(square != null && (square.piece instanceof Queen ||
                            ((vert != 0 && hori != 0) ? square.piece instanceof Bishop :
                            square.piece instanceof Rook)) && square.piece.color.equals(color)) {
                        return true;
                    }
                }
            }
            // Knight
            for(int primary = -2; primary < 3; primary += 4) {
                for(int offset = -1; offset < 2; offset += 2) {
                    square = relSquare(offset, primary);
                    if(square != null && square.piece instanceof Knight &&
                            square.piece.color.equals(color)) {
                        return true;
                    }
                    square = relSquare(primary, offset);
                    if(square != null && square.piece instanceof Knight &&
                            square.piece.color.equals(color)) {
                        return true;
                    }
                }
            }
            // Pawn
            int searchDir = color.equals(Piece.Color.WHITE) ? -1 : 1;
            for(int offset = -1; offset < 2; offset += 2) {
                square = relSquare(offset, searchDir);
                if(square != null && square.piece instanceof Pawn &&
                        square.piece.color.equals(color)) {
                    return true;
                }
            }
            return false;
        }

        /**
         * A method to check if two squares are the same.
         * @return true if the squares are the same
         */
        public boolean equals(Object obj) {
            if(obj instanceof Square) {
                Square o = (Square) obj;
                return (this.file == o.file && this.rank == o.rank);
            }
            return false;
        }

        /**
         * Gets the square's coordinate and formats it.
         * @return the Square's coordinates
         */
        public String toString() {
            return ""+file+rank;
        }
    }
}