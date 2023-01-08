package CarTrading.seller;

import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.OneShotBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;

import java.util.*;

public class CarSellerAgent extends Agent {
    private Hashtable<String, String> catalogue;

    private CarSellerGui myGui;

    protected void setup() {
        // Printout a welcome message
        System.out.println("Hello Seller - agent " + getAID().getName() + "is ready.");
        catalogue = new Hashtable<>();

        myGui = new CarSellerGui(this);
        myGui.showGui();

        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());
        ServiceDescription sd = new ServiceDescription();
        sd.setType("car-selling");
        sd.setName("JADE-car-trading");
        dfd.addServices(sd);
        try {
            DFService.register(this, dfd);
        } catch (FIPAException fe) {
            fe.printStackTrace();
        }

        addBehaviour(new OfferRequestsServer());
        addBehaviour(new PurchaseOrdersServer());

    }

    protected void takeDown() {
        try {
            DFService.deregister(this);
        } catch (FIPAException fe) {
            fe.printStackTrace();
        }
        myGui.dispose();
        System.out.println("Seller agent " + getAID().getName() + "terminating.");
    }

    public void updateCatalogue(final String car, final int mileage, final int price) {
        addBehaviour(new OneShotBehaviour() {
            @Override
            public void action() {
                catalogue.put(car, String.valueOf(mileage) + " " + String.valueOf(price));
                System.out.println("Seller agent - " + getAID().getName() + ": " + car + " inserted into catalogue." + "Mileage = " + mileage + ". Price = " + price);
            }
        });
    }

    public void showUnsoldCars() {
        Enumeration<String> keys = catalogue.keys();
        System.out.print("Unsold cars: ");
        while (keys.hasMoreElements()) {
            System.out.print(keys.nextElement() + "  ");
        }
        System.out.println();
    }

    private class OfferRequestsServer extends CyclicBehaviour {
        public void action() {
            MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.CFP);
            ACLMessage msg = myAgent.receive(mt);
            if (msg != null) {
                String[] requirements = msg.getContent().split(" "); // требования покупателя
                int mileageReq = Integer.parseInt(requirements[0]);
                int priceReq = Integer.parseInt(requirements[1]);
                ArrayList<String> cars = new ArrayList<String>(); // Список машин, которые подходят под условия
                for (String key : catalogue.keySet()) {
                    String[] cont = catalogue.get(key).split(" ");
                    int mileage = Integer.parseInt(cont[0]);
                    int price = Integer.parseInt(cont[1]);
                    if (mileage <= mileageReq && price <= priceReq) {
                        cars.add(key + " " + mileage + " " + price);
                    }
                }
                String answer = String.join(",", cars);

                ACLMessage reply = msg.createReply();

                if (answer.length() != 0) {
                    reply.setPerformative(ACLMessage.PROPOSE);
                    reply.setContent(answer);
                } else {
                    reply.setPerformative(ACLMessage.REFUSE);
                    reply.setContent("not-available");
                    block();
                }
                myAgent.send(reply);
            } else {
                block();
            }
        }
    }

    private class PurchaseOrdersServer extends CyclicBehaviour {
        public void action() {
            MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.ACCEPT_PROPOSAL);
            ACLMessage msg = myAgent.receive(mt);
            if (msg != null) {
                // ACCEPT_PROPOSAL Message received. Process it
                String car = msg.getContent();
                ACLMessage reply = msg.createReply();

                String[] cont = catalogue.remove(car).split(" ");
                String mileage = cont[0];
                String price = cont[1];
                if (price != null && mileage != null) {
                    reply.setPerformative(ACLMessage.INFORM);
                    System.out.println(getAID().getName() + " sold car " + car + "to agent " + msg.getSender().getName());
                    showUnsoldCars();
                } else {
                    reply.setPerformative(ACLMessage.FAILURE);
                    reply.setContent("not-available");
                }
                myAgent.send(reply);
            } else {
                block();
            }
        }
    }

}

