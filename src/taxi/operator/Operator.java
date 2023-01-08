package taxi.operator;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.Behaviour;
import jade.core.behaviours.TickerBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;


import java.util.Hashtable;
import java.util.concurrent.ThreadLocalRandom;


public class Operator extends Agent {

    private static final int TIME_MOD = 1000000;
    private static final int CHECK_TIME = 10000;
    private AID[] driverAgents = null;
    private AID[] clientAgents = null;


    @Override
    protected void setup() {
        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());
        ServiceDescription sd = new ServiceDescription();
        sd.setType("operator");
        sd.setName("JADE-taxi");
        dfd.addServices(sd);
        try {
            DFService.register(this, dfd);
        } catch (FIPAException fe) {
            fe.printStackTrace();
        }
        System.out.println("Operator-agent " + getAID().getName() + "is ready.");

        addBehaviour(new AgentsSearcher(this, CHECK_TIME));
    }

    @Override
    protected void takeDown() {
        try {
            DFService.deregister(this);
        } catch (FIPAException fe) {
            fe.printStackTrace();
        }
        System.out.println("Operator-agent " + getAID().getName() + "terminating.");
    }

    protected int calculateTime(int distance) {
        return TIME_MOD * distance;
    }

    private class AgentsSearcher extends TickerBehaviour {

        public AgentsSearcher(Agent a, long period) {
            super(a, period);
        }

        @Override
        protected void onTick() {
            DFAgentDescription templateForClients = new DFAgentDescription();
            ServiceDescription sdForClients = new ServiceDescription();
            sdForClients.setType("clients");
            templateForClients.addServices(sdForClients);


            DFAgentDescription templateForDrivers = new DFAgentDescription();
            ServiceDescription sdForDrives = new ServiceDescription();
            sdForDrives.setType("drivers");
            templateForDrivers.addServices(sdForDrives);

            try {
                System.out.println("Попытка найти агентов клиентов...");

                DFAgentDescription[] resultForClients = DFService.search(myAgent, templateForClients);
                System.out.println("Найдены следующие агенты клиенты:");
                clientAgents = new AID[resultForClients.length];
                for (int i = 0; i < resultForClients.length; ++i) {
                    clientAgents[i] = resultForClients[i].getName();
                    System.out.println(clientAgents[i].getName());
                }

                System.out.println("Попытка найти агентов водителей...");

                DFAgentDescription[] resultForDrivers = DFService.search(myAgent, templateForDrivers);
                System.out.println("Найдены следующие агенты водители:");
                driverAgents = new AID[resultForDrivers.length];
                for (int i = 0; i < resultForDrivers.length; ++i) {
                    driverAgents[i] = resultForDrivers[i].getName();
                    System.out.println(driverAgents[i].getName());
                }

            } catch (FIPAException fe) {
                fe.printStackTrace();
            }
            if (clientAgents.length == 0) {
                System.out.println("Нет клиентов!");
            } else if (driverAgents.length == 0) {
                System.out.println("Нет водителей!");
            } else {
                addBehaviour(new RequestPerformer());
            }
        }
    }

    public class RequestPerformer extends Behaviour {
        private AID bestDriver;
        private int bestDistance = Integer.MAX_VALUE;


        private AID currentClientAID;
        private String currentClientCoords;

        private int repliesCntDrivers = 0;
        private int step = 0;

        protected void cleanUp() {
            this.bestDriver = null;
            this.bestDistance = Integer.MAX_VALUE;
            this.currentClientCoords = null;
            this.currentClientAID = null;
        }

        @Override
        public void action() {
            switch (step) {
                case 0 -> {
                    // Получаем координаты от клиента
                    ACLMessage receive = myAgent.receive(
                            MessageTemplate.and(
                                    MessageTemplate.MatchPerformative(ACLMessage.CFP),
                                    MessageTemplate.MatchConversationId("clients-coords")
                            )
                    );

                    if (receive != null) {
                        System.out.println("---------------------------------");
                        currentClientCoords = receive.getContent();
                        currentClientAID = receive.getSender();
                        System.out.println("Получаем координаты от клиента (" + currentClientAID.getName() + ") " + currentClientCoords);
                        System.out.println("---------------------------------\n");
                        step = 1;
                    } else {
                        block();
                    }
                }
                case 1 -> {
                    // Отсылаем координаты к водителям
                    System.out.println("---------------------------------");
                    System.out.println("Отсылаем координаты клиента (" + currentClientAID.getName() + ") " + currentClientCoords + " к водителям!!!");
                    System.out.println("---------------------------------\n");

                    ACLMessage cfp = new ACLMessage(ACLMessage.CFP);

                    for (AID driverAgent : driverAgents) {
                        cfp.addReceiver(driverAgent);
                    }

                    cfp.setConversationId("client-coords-to-driver");
                    cfp.setReplyWith("cfp" + System.currentTimeMillis());
                    cfp.setContent(currentClientCoords);

                    myAgent.send(cfp);

                    step = 2;
                }
                case 2 -> {
                    // Получаем ответы от водителей.

                    ACLMessage receive = myAgent.receive(
                            MessageTemplate.and(
                                    MessageTemplate.MatchConversationId("driver-distance-to-client"),
                                    MessageTemplate.MatchPerformative(ACLMessage.PROPOSE)
                            )
                    );

                    if (receive != null) {
                        int distance = Integer.parseInt(receive.getContent());

                        if ((distance < bestDistance && distance != -1) || bestDriver == null) {
                            bestDriver = receive.getSender();
                            bestDistance = distance;
                            System.out.println("---------------------------------");
                            System.out.println("Водитель (" + bestDriver.getName() + ") подходит! Расстояние - " + bestDistance);
                            System.out.println("---------------------------------\n");
                        } else if (distance == -1) {
                            System.out.println("---------------------------------");
                            System.out.println("Водитель (" + receive.getSender().getName() + ") занят!");
                            System.out.println("---------------------------------\n");
                        } else {
                            System.out.println("---------------------------------");
                            System.out.println("Водитель (" + receive.getSender().getName() + ") не подходит! Расстояние - " + distance + " больше, чем лучшее " + bestDistance);
                            System.out.println("---------------------------------\n");
                        }


                        repliesCntDrivers++;

                        // приняты ответы от всех водителей
                        if (repliesCntDrivers >= driverAgents.length) {
                            step = 3;
                        }
                    } else {
                        block();
                    }
                }
                case 3 -> {

                    if (bestDistance != Integer.MAX_VALUE && bestDistance != -1) {
                        // Время вычисляется по формуле TIME_MOD * distance
                        int tripTime = calculateTime(bestDistance);

                        // Посылаем ответ клиенту (AID водителя, и время поездки)
                        System.out.println("---------------------------------");
                        System.out.println("Посылаем ответ " + currentClientAID.getName() + " (водителя (" + bestDriver.getName() + ") и время поездки (" + tripTime + "))!");
                        System.out.println("---------------------------------\n");

                        ACLMessage orderClient = new ACLMessage(ACLMessage.PROPOSE);
                        orderClient.addReceiver(currentClientAID);
                        orderClient.setContent(bestDriver.getName() + " " + tripTime);
                        orderClient.setConversationId("client-order-complete");
                        orderClient.setReplyWith("client-order" + System.currentTimeMillis());

                        myAgent.send(orderClient);

                        // Посылаем водителю сигнал и клиента, и делаем его недоступным на время.
                        System.out.println("---------------------------------");
                        System.out.println("Посылаем " + bestDriver.getName() + " сигнал и " + currentClientAID.getName() + ", и делаем его недоступным на время (" + tripTime + ")!");
                        System.out.println("---------------------------------\n");

                        ACLMessage orderDriver = new ACLMessage(ACLMessage.ACCEPT_PROPOSAL);
                        orderDriver.addReceiver(bestDriver);
                        orderDriver.setContent(currentClientAID.getName() + " " + tripTime);
                        orderDriver.setConversationId("driver-order-complete");
                        orderDriver.setReplyWith("driver-order" + System.currentTimeMillis());
                        myAgent.send(orderDriver);

                    }
                    cleanUp();
                    step = 4;
                }
            }
        }

        @Override
        public boolean done() {
            return (step == 4);
        }
    }

}
