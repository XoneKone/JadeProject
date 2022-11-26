package A;

import jade.core.Agent;
import jade.core.AID;
import jade.core.Timer;
import jade.domain.AMSService;
import jade.domain.FIPAAgentManagement.*;
import jade.core.behaviours.*;
import jade.lang.acl.*;

import java.util.concurrent.TimeUnit;


public class AMain extends Agent {

    public void setup() {
        System.out.println("Привет! агент " + getAID().getName() + " готов.");
        addBehaviour(new CyclicBehaviour(this) // Поведение агента исполняемое в цикле
        {
            public void action() {
                ACLMessage msg = receive();
                if (msg != null && !msg.getSender().equals(myAgent.getAID())) {
                    System.out.println(" – " + myAgent.getLocalName() + " received: " + msg.getContent());
                } //Вывод на экран локального имени агента и полученного сообщения
                block();
//Блокируем поведение, пока в очереди сообщений агента не появится хотя бы одно сообщение
            }
        });
        AMSAgentDescription[] agents = null;
        try {
            SearchConstraints c = new SearchConstraints();
            c.setMaxResults(Long.MAX_VALUE);
            agents = AMSService.search(this, new AMSAgentDescription(), c);
        } catch (Exception e) {
            System.out.println("Problem searching AMS: " + e);
            e.printStackTrace();
        }
        assert agents != null;
        for (AMSAgentDescription agent : agents) {
            AID agentID = agent.getName();
            ACLMessage msg = new ACLMessage(ACLMessage.INFORM);

            msg.addReceiver(agentID); // id агента которому отправляем сообщение
            msg.setLanguage("English"); //Язык
            msg.setContent("Ping"); //Содержимое сообщения
            if (!agentID.equals(getAID())) {
                send(msg); //отправляем сообщени
                System.out.println(" – " + getLocalName() + " send to: " + agentID.getLocalName());
            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
