package taxi.client;

import jade.core.AID;
import jade.core.Agent;
import jade.core.AgentState;
import jade.core.behaviours.Behaviour;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.SimpleBehaviour;
import jade.core.behaviours.WakerBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.wrapper.State;

import java.util.concurrent.ThreadLocalRandom;

import java.util.Hashtable;

/**
 * Клиент
 */
public class Client extends Agent {

    /**
     * Координаты (x, y)
     */
    private int x;
    private int y;
    private String personalDriver;
    private AID personalOperator;
    private int tripTime;

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public void setX(int x) {
        this.x = x;
    }

    public void setY(int y) {
        this.y = y;
    }

    public int getTripTime() {
        return tripTime;
    }

    public void setTripTime(int tripTime) {
        this.tripTime = tripTime;
    }

    public String getPersonalDriver() {
        return personalDriver;
    }

    public void setPersonalDriver(String personalDriver) {
        this.personalDriver = personalDriver;
    }

    private void setupNewCoords() {
        this.x = ThreadLocalRandom.current().nextInt(-5, 5 + 1);
        this.y = ThreadLocalRandom.current().nextInt(-5, 5 + 1);
        System.out.println(
                "Client-agent " + getAID().getName() + "is ready.\n" +
                        "Coordinates: x = " + this.getX() + ", y = " + this.getY() + "\n");
    }


    @Override
    protected void setup() {
        setupNewCoords();


        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());
        ServiceDescription sd = new ServiceDescription();
        sd.setType("clients");
        sd.setName("JADE-taxi");
        dfd.addServices(sd);
        try {
            DFService.register(this, dfd);
        } catch (FIPAException fe) {
            fe.printStackTrace();
        }


        DFAgentDescription dfAgentDescription = new DFAgentDescription();
        ServiceDescription serviceDescription = new ServiceDescription();
        serviceDescription.setType("operator");
        dfAgentDescription.addServices(serviceDescription);

        try {

            DFAgentDescription[] search = DFService.search(this, dfAgentDescription);
            personalOperator = search[0].getName();

        } catch (FIPAException fe) {
            fe.printStackTrace();
        }
        if (personalDriver == null) {
            addBehaviour(new TransferCoordsHandler());
        }
        addBehaviour(new OrderHandler());
    }

    @Override
    protected void takeDown() {
        try {
            DFService.deregister(this);
        } catch (FIPAException fe) {
            fe.printStackTrace();
        }
        System.out.println("Client-agent " + getAID().getName() + "terminating.");
    }

    private class TransferCoordsHandler extends SimpleBehaviour {
        private int step = 0;

        @Override
        public void action() {
            ACLMessage cfp = new ACLMessage(ACLMessage.CFP);
            cfp.addReceiver(personalOperator);
            cfp.setContent(getX() + " " + getY());
            cfp.setConversationId("clients-coords");
            cfp.setReplyWith("inf" + System.currentTimeMillis());
            myAgent.send(cfp);
            step = 1;
        }

        @Override
        public boolean done() {
            return step == 1;
        }

    }

    private class OrderHandler extends CyclicBehaviour {
        @Override
        public void action() {

            //  В ответ нам присылают ответ - Водителя, который повезет нас, и время выполнения заказа.
            ACLMessage receive = myAgent.receive(
                    MessageTemplate.and(
                            MessageTemplate.MatchConversationId("client-order-complete"),
                            MessageTemplate.MatchPerformative(ACLMessage.PROPOSE)
                    )
            );

            if (receive != null) {
                String[] driverAndTripTime = receive.getContent().split(" ");
                setPersonalDriver(driverAndTripTime[0]);
                setTripTime(Integer.parseInt(driverAndTripTime[1]));

                myAgent.doWait(getTripTime());

                if (myAgent.getState() == AP_ACTIVE) {
                    System.out.println("---------------------------------");
                    System.out.println(getName() + " прибыл в пункт назначения");
                    System.out.println("---------------------------------\n");
                    myAgent.doDelete();
                }

            } else {
                block();
            }

        }

    }

}

