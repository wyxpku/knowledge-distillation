package com.antbrains.mqtool;

import javax.jms.DeliveryMode;
import javax.jms.JMSException;
import javax.jms.MapMessage;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.QueueSender;
import javax.jms.QueueSession;
import javax.jms.TextMessage;

import org.apache.log4j.Logger;

public class ActiveMqSender implements MqSender {
	public static int PERSISTENT = DeliveryMode.PERSISTENT;
	public static int NON_PERSISTENT = DeliveryMode.NON_PERSISTENT;

	private static Logger logger = Logger.getLogger(ActiveMqSender.class);
	private QueueSession session = null;
	private String queueName = null;
	private QueueSender sender = null;
	private Queue senderQueue = null;

	public ActiveMqSender() {
	}

	public ActiveMqSender(QueueSession session, String queueName) {
		this.session = session;
		this.queueName = queueName;
	}

	@Override
	public boolean init(int persitentType) {
		if (this.session == null || this.queueName == null || this.queueName.isEmpty()) {
			logger.fatal("session or queueName must not be null or empty");
			return false;
		}
		try {
			// create queue
			senderQueue = this.session.createQueue(this.queueName);
			sender = this.session.createSender(senderQueue);
			sender.setDeliveryMode(persitentType);
		} catch (Exception e) {
			logger.fatal("init error");
			logger.fatal(e.getMessage(), e);
			return false;
		}
		return true;
	}

	@Override
	public boolean send(String msg) {
		if (msg == null || sender == null) {
			return false;
		}
		try {
			TextMessage tMsg = this.session.createTextMessage(msg);

			this.sender.send(tMsg);
		} catch (JMSException e) {
			logger.error(e.getMessage(), e);
			return false;
		}
		return true;
	}

	public boolean send(Message msg) {
		if (msg == null || sender == null) {
			logger.warn("msg is null");
			return false;
		}
		try {
			this.sender.send(msg);
			return true;
		} catch (Exception e) {
			logger.error("send msg error");
			logger.error(e.getMessage(), e);
		}
		return false;
	}

	@Override
	public boolean send(Object obj) {
		if (obj instanceof Message) {
			return send((Message) obj);
		}
		return false;
	}

	@Override
	public long getQueueSize() {
		if (session == null) {
			return -1;
		}
		MessageProducer producer = null;
		MessageConsumer consumer = null;
		try {
			Queue replyTo = session.createTemporaryQueue();
			consumer = session.createConsumer(replyTo);

			String queueName = "ActiveMQ.Statistics.Destination." + this.queueName;
			Queue testQueue = session.createQueue(queueName);
			producer = session.createProducer(testQueue);
			Message msg = session.createMessage();
			msg.setJMSReplyTo(replyTo);
			producer.send(msg);

			MapMessage reply = (MapMessage) consumer.receive(1000);
			if (reply != null) {
				return (Long) reply.getObject("size");
			} else {
				return -1;
			}
		} catch (JMSException e) {
			logger.error(e.getMessage(), e);
		} finally {
			if (producer != null) {
				try {
					producer.close();
				} catch (JMSException e) {
					logger.error(e.getMessage(), e);
				}
			}
			if (consumer != null) {
				try {
					consumer.close();
				} catch (JMSException e) {
					logger.error(e.getMessage(), e);
				}
			}
		}

		return 0;
	}

	@Override
	public void destroy() {
		try {
			if (sender != null) {
				sender.close();
			}
			if (session != null) {
				session.close();
			}

		} catch (JMSException e) {
			logger.error(e.getMessage(), e);
		}
	}

	/* getter and setter */
	public QueueSession getSession() {
		return session;
	}

	public void setSession(QueueSession session) {
		this.session = session;
	}

	public String getQueueName() {
		return queueName;
	}

	public void setQueueName(String queueName) {
		this.queueName = queueName;
	}

	public QueueSender getSender() {
		return sender;
	}

	public void setSender(QueueSender sender) {
		this.sender = sender;
	}

	public Queue getSenderQueue() {
		return senderQueue;
	}

	public void setSenderQueue(Queue senderQueue) {
		this.senderQueue = senderQueue;
	}

	@Override
	public boolean send(String msg, int prior) {
		if (msg == null || sender == null) {
			return false;
		}
		try {
			TextMessage tMsg = this.session.createTextMessage(msg);
			tMsg.setJMSPriority(prior);
			this.sender.send(tMsg);
		} catch (JMSException e) {
			logger.error(e.getMessage(), e);
			return false;
		}
		return true;
	}

	@Override
	public void commit() throws JMSException {
		this.session.commit();
	}

}
