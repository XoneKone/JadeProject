package taxi.driver;

import jade.core.Agent;
import jade.core.behaviours.Behaviour;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.WakerBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;

import java.util.Arrays;
import java.util.concurrent.ThreadLocalRandom;

enum Status {
    FREE, BUSY
}

public class Driver extends Agent {
    /**
     * Координаты (x, y)
     */
    private int x;
    private int y;

    private Status status = Status.FREE;
    private String personalClient;

    private int tripTime;

    public int getTripTime() {
        return tripTime;
    }

    public void setTripTime(int tripTime) {
        this.tripTime = tripTime;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public Status getStatus() {
        return this.status;
    }

    public String getPersonalClient() {
        return personalClient;
    }

    public void setX(int x) {
        this.x = x;
    }

    public void setY(int y) {
        this.y = y;
    }


    public void setStatus(Status status) {
        this.status = status;
    }

    public void setPersonalClient(String personalClient) {
        this.personalClient = personalClient;
    }

    private double calculateDistance(int x, int y) {
        return Math.sqrt(Math.pow((getX() - x), 2) + Math.pow((getY() - y), 2));
    }

    private void setupNewCoords() {
        this.x = ThreadLocalRandom.current().nextInt(-5, 5 + 1);
        this.y = ThreadLocalRandom.current().nextInt(-5, 5 + 1);
        System.out.println(
                "Driver-agent " + getAID().getName() + "is ready.\n" +
                        "Coordinates: x = " + this.getX() + ", y = " + this.getY() + "\n");
    }

    @Override
    protected void setup() {
        setupNewCoords();


        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());
        ServiceDescription sd = new ServiceDescription();
        sd.setType("drivers");
        sd.setName("JADE-taxi");
        dfd.addServices(sd);

        try {
            DFService.register(this, dfd);
        } catch (FIPAException fe) {
            fe.printStackTrace();
        }
        addBehaviour(new CalculateDistanceHandler());
        addBehaviour(new OrderHandler());
    }

    @Override
    protected void takeDown() {

        try {
            DFService.deregister(this);
        } catch (FIPAException fe) {
            fe.printStackTrace();
        }
        System.out.println("Driver-agent " + getAID().getName() + "terminating.");
    }

    private class CalculateDistanceHandler extends CyclicBehaviour {

        @Override
        public void action() {
            // В начале получаем координаты клиента от Оператора
            ACLMessage receiveMessage = myAgent.receive(
                    MessageTemplate.and(
                            MessageTemplate.MatchConversationId("client-coords-to-driver"),
                            MessageTemplate.MatchPerformative(ACLMessage.CFP)
                    )
            );

            if (receiveMessage != null) {

                String[] clientAndCoords = receiveMessage.getContent().split(" ");


                int curX = Integer.parseInt(clientAndCoords[0]);
                int curY = Integer.parseInt(clientAndCoords[1]);
                double dist = calculateDistance(curX, curY);

                // Отправляем в ответ клиента и расстояние до него
                ACLMessage reply = receiveMessage.createReply();
                reply.setPerformative(ACLMessage.PROPOSE);
                reply.setConversationId("driver-distance-to-client");

                if (getPersonalClient() != null) {
                    System.out.println(getName() + "Занят" + getStatus().toString());
                    reply.setContent("-1");
                } else {
                    reply.setContent(String.valueOf((int) dist));
                }
                myAgent.send(reply);
            } else {
                block();
            }

        }
    }

    private class OrderHandler extends CyclicBehaviour {
        @Override
        public void action() {

            // В ответ нам присылают ответ - Водителя, который повезет нас, и время выполнения заказа.
            ACLMessage receiveMessage = myAgent.receive(
                    MessageTemplate.and(
                            MessageTemplate.MatchConversationId("driver-order-complete"),
                            MessageTemplate.MatchPerformative(ACLMessage.ACCEPT_PROPOSAL)
                    )
            );

            if (receiveMessage != null) {
                String[] clientAndTime = receiveMessage.getContent().split(" ");
                setPersonalClient(clientAndTime[0]);
                setTripTime(Integer.parseInt(clientAndTime[1]));

                myAgent.doWait(getTripTime());

                if (myAgent.getState() == AP_ACTIVE) {
                    setPersonalClient(null);
                    setupNewCoords();
                }

            } else {
                block();
            }
        }
    }


}
