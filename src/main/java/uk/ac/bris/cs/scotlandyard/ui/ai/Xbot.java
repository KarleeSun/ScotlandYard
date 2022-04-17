package uk.ac.bris.cs.scotlandyard.ui.ai;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import io.atlassian.fugue.Pair;

import uk.ac.bris.cs.scotlandyard.model.*;

/*
    是要给每个选择一个分数
    那就直接重写getAvailableMoves
    用的时候判断 想走的move是不是在getAvailableMoves获得的moves里面
    如果在的话就选择那个move
    然后对现在的局面有一个评分标准
    之后minimax就是看分的
 */

/*
    这个事情是这样的，其实不是给它每一轮的分数，是给一个几轮之后的分数（几轮就是depth）
    然后再倒着找 看导致这个最优结果的是哪一个move
 */

/*
    问题：mrX能用secret和double时候moves里面没有
    predictDetectives排列组合有问题
 */

public class Xbot implements Ai {
    //成员变量及其初始化
    //这些可以考虑改成public 因为每个class都用了其实
    //而且这大概还能再简化吧..看着好丑
//    private static Board board; //这样在这个class就能直接用，不用每个函数都再传入一遍board
    private Piece.MrX MRX; //mrX的piece
    private List<Piece.Detective> detectives; //所有detective的pieces，存在一个list里
    private int mrXLoc; //记录mrX的位置和预测位置 相当于move的destination
    private List<Integer> detectivesLoc; //记录detectives的位置 按顺序存在一个list里 相当于是destination
    private int source; //相当于一个move的source，也许没必要，先写着
//    private List<Integer> detectiveSources; //同上，存detectives的sources
    private Map<ScotlandYard.Ticket, Integer> mrXTickets; //存的是mrX的票
    private Map<ScotlandYard.Ticket, Integer> detectivesTickets; //存的是detective的票
    private Boolean useDouble; //有没有用double票
    private Boolean useSecret; //有没有用secret票
    private int turnNum; //记录一下轮数
    private ScotlandYard.Ticket usedTicket; //存那个detective他用的票


    //name of this AI
    @Nonnull
    @Override
    public String name() {
        return "Xbot";
    }

    //pick the best move for mrX
    @Nonnull
    @Override
    public Move pickMove(@Nonnull Board board, Pair<Long, TimeUnit> timeoutPair) {
        Minimax minimax = new Minimax();
        setUp(board); //在这setup了，可是在这外面，这个class里没有set
        System.out.println("mrXTickets: "+mrXTickets);
        Minimax.TreeNode node = minimax.tree(board,null,0,3,minimax.MIN,minimax.MAX,mrXLoc,detectivesLoc,turnNum,false,false,mrXTickets,detectivesTickets);
        return minimax.miniMaxAlphaBeta(node,3,false,minimax.MIN,minimax.MAX).move;
    }

    //=========================================================================================
    //这些不用再改了
    //这个setup它set了一个寂寞

    private void setUp(@Nonnull Board board) {
        MRX = Piece.MrX.MRX;
        detectives = getAllDetectives(board);
        mrXLoc = getCurrentMrXLoc(board);
        detectivesLoc = getCurrentDetectivesLoc(board);
        source = -1;
        mrXTickets = getCurrentMrXTickets(board);
        detectivesTickets = getCurrentDetectiveTickets(board);
        useDouble = false;
        useSecret = false;
        turnNum = board.getMrXTravelLog().size();
    }


    //get mrX current location
    private int getCurrentMrXLoc(@Nonnull Board board) {
        return board.getAvailableMoves().stream().iterator().next().source();
    }

    //get detectives location and return as a list
    private List<Integer> getCurrentDetectivesLoc(@Nonnull Board board) {

        //get pieces of detectives
        List<Piece.Detective> detectives = getAllDetectives(board);
        System.out.println(detectives);
        //get detectives locations and store them in a list
        List<Integer> detectiveLocations = new ArrayList<>();
        for (Piece.Detective detectivePiece : detectives) {
            detectiveLocations.add(board.getDetectiveLocation(detectivePiece).get());
            System.out.println(detectivePiece + " " + board.getDetectiveLocation(detectivePiece).get());
        }
        return detectiveLocations;
    }

    public Map<ScotlandYard.Ticket, Integer> getCurrentMrXTickets(@Nonnull Board board) {
        Map<ScotlandYard.Ticket, Integer> mrXTickets = new HashMap<>();
        for (ScotlandYard.Ticket t : ScotlandYard.Ticket.values())
            mrXTickets.put(t, board.getPlayerTickets(MRX).get().getCount(t));
        return mrXTickets;
    }

    private Map<ScotlandYard.Ticket, Integer> getCurrentDetectiveTickets(@Nonnull Board board) {
        detectivesTickets = new HashMap<>();
        for (Piece.Detective d : detectives) {
            for (ScotlandYard.Ticket t : ScotlandYard.Ticket.values()) {
                if (!detectivesTickets.containsKey(t))
                    detectivesTickets.put(t, board.getPlayerTickets(d).get().getCount(t));
                if (detectivesTickets.containsKey(t))
                    detectivesTickets.put(t, detectivesTickets.get(t) + board.getPlayerTickets(d).get().getCount(t));
            }
        }
        return detectivesTickets;
    }

    //得到所有存着detectives的piece
    public List<Piece.Detective> getAllDetectives(@Nonnull Board board) {
        List<Piece> allDetectivePieces = new ArrayList<Piece>();
        allDetectivePieces.addAll(board.getPlayers());
        allDetectivePieces.remove("MRX");

        List<Piece.Detective> detectives = new ArrayList<>();
        for (Piece detective : allDetectivePieces) {
            if (detective.isDetective()) detectives.add((Piece.Detective) detective);
        }
        return detectives;
    }
    //====================================================================================================

    public List<Move> predictMrXMoves(@Nonnull Board board, Piece MRX,int mrXLoc, List<Integer> detectivesLoc,Map<ScotlandYard.Ticket, Integer> mrXTickets){

        List<Move> moves = new ArrayList<>();
        moves.addAll(makeSingleMoves(board, MRX,mrXLoc, detectivesLoc,mrXTickets));
        moves.addAll(makeDoubleMoves(board, MRX,mrXLoc, detectivesLoc,mrXTickets));
        System.out.println("moves: "+moves);
        return moves;
    }

    //对于mrX
    //应该是确定走这一步了才能更新，就是在那个树里更新而不是在这更新
    private List<Move.SingleMove> makeSingleMoves(@Nonnull Board board, Piece MRX, int mrXLoc, List<Integer> detectivesLoc,
                                                  Map<ScotlandYard.Ticket, Integer> mrXTickets) {
        source = mrXLoc;
        System.out.println("mrXTickets: 99"+mrXTickets);
        List<Move.SingleMove> possibleMoves = new ArrayList<>();
        Set<Integer> destination = board.getSetup().graph.adjacentNodes(source);
        for (int d : destination) {
            if (mrXTickets.get(ScotlandYard.Ticket.SECRET) >= 1 && (!detectivesLoc.contains(d))) {
                possibleMoves.add(new Move.SingleMove(MRX, source, ScotlandYard.Ticket.SECRET, d));
            }
            for (ScotlandYard.Transport t : board.getSetup().graph.edgeValueOrDefault(source, d, ImmutableSet.of())) {
                if (mrXTickets.get(t.requiredTicket()) >= 1 && (!detectivesLoc.contains(d))) {
                    possibleMoves.add(new Move.SingleMove(MRX, mrXLoc, t.requiredTicket(), d));
                }
            }
        }
        return possibleMoves;
    }

    private List<Move.DoubleMove> makeDoubleMoves(@Nonnull Board board,Piece MRX, int mrXLoc, List<Integer> detectivesLoc,
                                                  Map<ScotlandYard.Ticket, Integer> mrXTickets){
        //先判断它有没有double票
        if (mrXTickets.get(ScotlandYard.Ticket.DOUBLE) == 0) return new ArrayList<>();
        mrXTickets.put(ScotlandYard.Ticket.DOUBLE,mrXTickets.get(ScotlandYard.Ticket.DOUBLE)-1); //减一张double票
        List<Move.DoubleMove> doubleAvailableMoves = new ArrayList<>();
        //store all available first move by invoke makeSingleMove method
        List<Move.SingleMove> firstAvailableMoves = new ArrayList<>(makeSingleMoves(board,MRX,mrXLoc,detectivesLoc,mrXTickets));
        //iterate through all possible single moves and store its corresponding second move
        for (Move.SingleMove firstMove : firstAvailableMoves){
            /* store second available moves*/
            List<Move.SingleMove> secondAvailableMoves = new ArrayList<>(
                    makeSingleMoves(board,MRX,firstMove.destination,detectivesLoc, mrXTickets));
            //iterate through all possible second move for a particular first move and create new double move and store it
            ScotlandYard.Ticket ticketUsed = firstMove.ticket;
            Integer ticketLeft = mrXTickets.get(ticketUsed);
            if (!secondAvailableMoves.isEmpty()) {
                for (Move.SingleMove secondMove : secondAvailableMoves) {
                    if (!(secondMove.ticket == ticketUsed) || ticketLeft >= 2) {
                        doubleAvailableMoves.add( new Move.DoubleMove(MRX, firstMove.source(),
                                firstMove.ticket, firstMove.destination, secondMove.ticket, secondMove.destination));
                    }
                }
            }
        }
        return doubleAvailableMoves;
    }

    //------------------------------------------------------------------------------------------------------------------

    //用来在minimax中预测detectives会做的理论最优选择
    //对于detective，无所谓他选择的move，所以只返回更新过后的位置
    //detectives的问题也一样，他们的票和位置应该在树里更新而不是在这更新
    //应该是对所有detectives能走的情况都处理，而不仅仅是处理最优情况
    //所以还要再重写这个函数
    //要么这个就返回二维数组 存所有可能的detectives的位置
    public List<List<Map<Integer, ScotlandYard.Ticket>>> predictDetectiveMoves(@Nonnull Board board, int mrXLoc, List<Integer> detectivesLoc,
                                                                               Map<ScotlandYard.Ticket, Integer> detectivesTickets){
        System.out.println("detectivesTTickets: "+detectivesTickets);
        detectives = getAllDetectives(board);
        List<List<Map<Integer, ScotlandYard.Ticket>>> allDetectiveMoves = new ArrayList<>();
        List<Map<Integer,ScotlandYard.Ticket>> oneDetectiveMoves = new ArrayList<>();
        Map<Integer,ScotlandYard.Ticket> locWithTicket = new HashMap<>();
        Map<ScotlandYard.Ticket,Integer> detectivesTicketsCopy = detectivesTickets;
        //然后每个detective都走到了距离-1的地方
        for(Integer dLoc: detectivesLoc){ //对于每一个detective
            detectivesTicketsCopy = detectivesTickets;
            Dijkstra dj = new Dijkstra(mrXLoc, dLoc, board);
            int distance = dj.getDistance();
            for (int d: board.getSetup().graph.adjacentNodes(dLoc)) {
                Dijkstra dk = new Dijkstra(mrXLoc, d, board);
                if (!detectivesLoc.contains(d) && dk.getDistance() == distance - 1) {
                    for (ScotlandYard.Transport t : board.getSetup().graph.edgeValueOrDefault(dLoc, d, ImmutableSet.of())) {
                        System.out.println("1, detectives Tickets:" + detectivesTickets);
                        System.out.println("t.required ticket: "+ detectivesTicketsCopy.get(t.requiredTicket()));
                        if (detectivesTicketsCopy.get(t.requiredTicket()) >= 1) {
                            System.out.println("aaa");
                            locWithTicket.put(d, t.requiredTicket());
                            detectivesTicketsCopy.put(t.requiredTicket(),detectivesTicketsCopy.get(t.requiredTicket())-1);
                            System.out.println("loc with ticket: "+locWithTicket);
                            oneDetectiveMoves.add(Map.copyOf(locWithTicket));
                            System.out.println("one d moves: "+ oneDetectiveMoves);
                            locWithTicket.clear();
                            System.out.println("one dd moves: "+oneDetectiveMoves);
                        }
                    }
                }
            }
            System.out.println("one detective moves: "+oneDetectiveMoves);
            allDetectiveMoves.add(List.copyOf(oneDetectiveMoves));
            System.out.println("0.all detectivemoves: "+allDetectiveMoves);
            oneDetectiveMoves.clear();
        }
        System.out.println("allDetectiveMoves: "+allDetectiveMoves);
        List<List<Map<Integer, ScotlandYard.Ticket>>> allDetectiveMovesResult = new ArrayList<>();
        System.out.println("haha: "+ allDetectiveMoves.stream().toList());
        allDetectiveMovesResult = Lists.cartesianProduct(allDetectiveMoves.stream().toList());
        System.out.println("allDetectiveMovesResult: "+allDetectiveMovesResult);
        return allDetectiveMovesResult;
    }

//    private List<Map<Integer, ScotlandYard.Ticket>> descartes(List<Map<Integer, ScotlandYard.Ticket>>... lists) {
//        List<Map<Integer, ScotlandYard.Ticket>> tempList = new ArrayList<>();
//        for (List<Map<Integer, ScotlandYard.Ticket>> list : lists) {
//            if (tempList.isEmpty()) {
//                tempList = list;
//            } else {
//                //java8新特性，stream流
//                tempList = tempList.stream().flatMap(item -> list.stream().map(item2 -> item + " " + item2)).collect(Collectors.toList());
//            }
//        }
//        return tempList;
//    }
//}

    //就只给两种情况吧 踩上了或者轮数到了 或者moves是空的
    public Boolean hasWinner(@Nonnull Board board, int mrXLoc, List<Integer> detectivesLoc) {
        //mr X being caught
        if(detectivesLoc.contains(mrXLoc)) return true;
        //no more round left  && detectives all made moves for this round
        if (turnNum == board.getSetup().moves.size()) return true;
        //或者moves空的
//        if(predictMrXMoves(board,mrXLoc,detectivesLoc,mrXTickets).isEmpty() || predictDetectiveMoves(board, mrXLoc, detectivesLoc).isEmpty())
//            return true;
        //no winner yet
        return false;
    }


    /*
        多数情况可能doublemove都比singlemove要好，但是double卡是有限的
        所以在离得远的时候就不用double卡 离得近时候（近到一定程度，再用double）

        给的都应该是destination，即可能到的位置 而不是mrX现在的位置
        visitor pattern的作用是知道用的是singlemove还是doublemove 这样就可以get所需要的位置

        先只处理single，即：在普通的时候只用singleMove，只有在特殊的时候才用doublemove

     */

    //这个函数用来得到我想得到的全部信息
    public Map<String, Object> getMoveInformation(Move move){
        System.out.println("move: "+ move);
        Minimax minimax = new Minimax();
        Map<String,Object> moveInfo = move.accept(new Move.Visitor<Map<String,Object>>(){
            Map<String, Object> moveInfo = new HashMap<>();
            @Override
            public Map<String,Object> visit(Move.SingleMove singleMove){
                System.out.println("singleMove: "+ singleMove);
//                System.out.println("moveInfo： "+moveInfo);
                //moveInfo = Map.of()可以将多个元素一次性添加进去
                moveInfo.put("isDoubleMove",false);
                moveInfo.put("piece",singleMove.commencedBy());
                moveInfo.put("source",singleMove.source());
                moveInfo.put("ticket",singleMove.ticket);
                moveInfo.put("destination",singleMove.destination);
                moveInfo.put("useSecret",false);
                if(singleMove.ticket == ScotlandYard.Ticket.SECRET) moveInfo.put("useSecret",true);
                System.out.println("Move info: "+moveInfo);
                return moveInfo;
            }
            @Override
            public Map<String,Object> visit(Move.DoubleMove doubleMove){
                System.out.println("doublemove: "+doubleMove);
                moveInfo.put("isDoubleMove",true);
                moveInfo.put("piece",doubleMove.commencedBy());
                moveInfo.put("source",doubleMove.source());
                moveInfo.put("ticket1",doubleMove.ticket1);
                moveInfo.put("destination1",doubleMove.destination1);
                moveInfo.put("ticket2",doubleMove.ticket2);
                moveInfo.put("destination2",doubleMove.destination2);
                moveInfo.put("useSecret",false);
                if(doubleMove.ticket1 == ScotlandYard.Ticket.SECRET || doubleMove.ticket2 == ScotlandYard.Ticket.SECRET)
                    moveInfo.put("useSecret",true);
                return moveInfo;
            }
        });
        return moveInfo;
    }

    //把mrX现在所有的available moves和其对应分数存入一个map
    private Map<Move,Integer> movesWithScore(@Nonnull Board board, Move move){
        Map<Move,Integer> moveWithScore = new HashMap<>();
        ImmutableSet<Move> moves = board.getAvailableMoves();
        for(Move availableMove: moves){
            moveWithScore.put(availableMove, 0);
        }
        return moveWithScore;
    }
}




//new一个map 把Move和对应的分数放进去
//        Map<Move,Integer> moveWithMark = new HashMap<>();
//        for(Move move: board.getAvailableMoves().asList()){
//            moveWithMark.put(move,giveMark(board,move));
//        }
////        System.out.println("moveWithMark: "+ moveWithMark);
//        //比较哪一个分最高，选分最高的那个move
//        List<Map.Entry<Move,Integer>> listMark = new ArrayList<>(moveWithMark.entrySet());
//        Collections.sort(listMark, (o1,o2) -> (o2.getValue() - o1.getValue())); //降序排列
//        Move pickedMove = listMark.get(0).getKey();

//        //测试用
//        System.out.println("listMark: " + listMark);
//        System.out.println("pickedMove: " + pickedMove);

//        return pickedMove;

//给一个位置（source）返回它所有可能的move
//其实可以更新mrX和detectives的位置
//对于每一个位置 找他的adjacencyNodes 然后判断它有没有票
//在普通轮double票就特别贵 在detectives离得特别近时候、还有reveal之后，double票就变得便宜
