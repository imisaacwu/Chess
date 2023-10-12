import java.util.*;

public class Client {
    public static void main(String[] args) {
        Scanner console = new Scanner(System.in);
        // String pre = "Rxh8 Ke7 Raxa8 Kd7 Rh6 Kc7 Ra1 Kb7 Rb1 Ka7 Kd2 Ka8 Kc3 Ka7 Kb4 Kb8 Kb5 Ka8 Kb6 Kb8";
        // String pre = "e4 e5 Nf3 d6 d4 Bg4 dxe5 Bxf3 Qxf3 dxe5 Bc4 Nf6 Qb3 Qe7 Nc3 c6 Bg5 b5 Nxb5 cxb5 Bxb5 Nbd7 O-O-O Rd8 Rxd7 Rxd7 Rd1 Qe6 Bxd7 Nxd7";
        // String pre = "Nf3 Nf6 c4 g6 Nc3 Bg7 d4 O-O Bf4 d5 Qb3 dxc4 Qxc4 c6 e4 Nbd7 Rd1 Nb6 Qc5 Bg4 Bg5 Na4 Qa3 Nxc3 bxc3 Nxe4 Bxe7 Qb6 Bc4 Nxc3 Bc5 Rfe8+ Kf1 Be6 Bxb6 Bxc4+ Kg1 Ne2+ Kf1 Nxd4+ Kg1 Ne2+ Kf1 Nc3+ Kg1 axb6 Qb4 Ra4 Qxb6 Nxd1 h3 Rxa2 Kh2 Nxf2 Re1 Rxe1 Qd8+ Bf8 Nxe1 Bd5 Nf3 Ne4 Qb8 b5 h4 h5 Ne5 Kg7 Kg1 Bc5+ Kf1 Ng3+ Ke1 Bb4+ Kd1 Bb3+ Kc1 Ne2+ Kb1 Nc3+ Kc1 Rc2";
        // String pre = "e4 e5 Qf3 a6 Bc4 b5 Qxf7";
        String pre = "e4 e5 d4 d5 exd5 exd4 a3 Bc5 b4 Nf6";// Qe2";
        String FEN_STANDARD = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";
        String AverbakhVKotov = "r1bq1rk1/pp1nbppp/2pp1n2/4p3/2PPP3/2N2N2/PP2BPPP/R1BQ1RK1 w - - 0 8";
        Chess game = new Chess("8/8/8/5K2/8/8/8/8 b Q - 49 1");

        System.out.println(game.instructions());
        System.out.println();

        while (!game.isGameOver()) {
            System.out.println(game);
            System.out.printf("Player %d's turn.\n", game.getNextPlayer());
            try {
                game.makeMove(console);
            } catch (IllegalArgumentException ex) {
                System.out.println("**Illegal move: " + ex.getMessage());
            }
        }
        System.out.println(game);
        int winner = game.getWinner();
        if (winner > 0) {
            System.out.printf("Player %d wins!\n", winner);
        } else {
            System.out.println("It's a tie!");
        }
    }
}