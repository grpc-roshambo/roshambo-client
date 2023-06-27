package com.grpcroshambo.roshamboclient.boundary;

import com.grpcroshambo.roshambo.*;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Service
public class RoshamboService {
    private static final Logger logger = LoggerFactory.getLogger(RoshamboService.class.getName());
    private static final Random random = new Random();

    private final static HashMap<String, HashMap<Result, AtomicInteger>> playerStatistics = new HashMap<>();

    @GrpcClient("roshambo")
    private RoshamboServiceGrpc.RoshamboServiceStub roshamboServiceStub;

    public void joinAs(final String name) {
        try {
            final var request = JoinRequest.newBuilder()
                    .setName(name)
                    .build();
            roshamboServiceStub.join(request, new StreamObserver<>() {
                @Override
                public void onNext(MatchRequestFromServer matchRequestFromServer) {
                    logger.info("Received match request with token=" + matchRequestFromServer.getMatchToken());
                    Choice choice = Choice.forNumber(random.nextInt(3) + 1);
                    logger.info("Choice=" + choice + " seems to be a good choice for matchToken=" + matchRequestFromServer.getMatchToken());
                    MatchChoice matchChoice = MatchChoice
                            .newBuilder()
                            .setMatchToken(matchRequestFromServer.getMatchToken())
                            .setChoice(choice)
                            .build();
                    roshamboServiceStub.play(matchChoice, new StreamObserver<>() {
                        @Override
                        public void onNext(MatchResult matchResult) {
                            logger.info("Matchresult for matchToken=" + matchResult.getMatchToken() + " is " + matchResult.getResult());
                            playerStatistics.get(name).get(matchResult.getResult()).incrementAndGet();
                        }

                        @Override
                        public void onError(Throwable throwable) {
                            logger.error("Error occurred while playing", throwable);
                        }

                        @Override
                        public void onCompleted() {
                            logger.info("Completed single game play");
                        }
                    });
                }

                @Override
                public void onError(Throwable throwable) {
                    logger.error("Error occurred while joining", throwable);
                }

                @Override
                public void onCompleted() {
                    logger.info("Completed join");
                }
            });
            HashMap<Result, AtomicInteger> playerStatistic = new HashMap<>();
            for (final var result : Result.values()) {
                playerStatistic.put(result, new AtomicInteger(0));
            }
            playerStatistics.put(name, playerStatistic);
        } catch (Exception e) {
            logger.error("Exception ocurred while joining", e);
        }
    }

    @PostConstruct
    public void init() {
        for (int i = 0; i < 50; i++) {
            joinAs("Max" + i);
        }
    }

    @Scheduled(cron = "5/10 * * * * *")
    public void statistic() {
        logger.info("Current statistic:");
        for (var playerWin : playerStatistics.entrySet()) {
            logger.info(playerWin.getKey() + ":" + playerWin.getValue().entrySet().stream().map(resultAtomicIntegerEntry -> resultAtomicIntegerEntry.getKey() + "-" + resultAtomicIntegerEntry.getValue() + ";").collect(Collectors.joining()));
        }
    }
}