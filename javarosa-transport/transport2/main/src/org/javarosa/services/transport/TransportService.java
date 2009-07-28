package org.javarosa.services.transport;

import java.io.IOException;
import java.util.Date;
import java.util.Vector;

/**
 * The TransportService is generic and should not need to be changed when adding
 * new kinds of transport or new kinds of messages
 * 
 * To add a new kind of transport, it is necessary to define
 * <ol>
 * <li>A new kind of <b>TransportMessage</b>
 * <li>A new kind of <b>Transporter</b> - an object with the ability to send one
 * of the new kinds of message
 * </ol>
 * 
 * The TransportMessage interface is such that each TransportMessage is able to
 * create an appropriate Transporter (via the <code>createTransporter()</code>
 * method) able to send it.
 * 
 * This is not ideal. To separate these would seem to make sense. Then we'd
 * have:
 * 
 * <code>
 * TransportMessage m = new SomeTransportMessage() 
 * Transporter tr = new SomeTransporter(m); 
 * new TransportService().send(tr);
 * </code>
 * 
 * The most intuitive programmer interface however involves the following steps
 * alone:
 * <ol>
 * <li>create a Message
 * <li>send the Message
 * </ol>
 * 
 * <code>
 * TransportMessage m = new SomeTransportMessage() 
 * new TransportService().send(m);
 * </code>
 * 
 * Requiring a Message to be able to create a Transporter is considered a price
 * worth paying.
 */
public class TransportService {

	/**
	 * 
	 * The TransportService has a messageStore, in which all messages to be sent
	 * are persisted immediately
	 * 
	 */
	private static TransportMessageStore MESSAGE_STORE = new TransportMessageStore();
	private static TransportMethodStore METHOD_STORE = new TransportMethodStore();

	/**
	 * 
	 * Send a message in a thread, using the default number of tries and the
	 * default pause between tries
	 * 
	 * Sending a message happens like this:
	 * 
	 * <ol>
	 * <li>The message creates an appropriate Transporter (which contains the
	 * message)
	 * <li>The message is given a QueuingDeadline, equal to the maximum time it
	 * can spend in a QueuingThread
	 * <li>The message is persisted in the Message Store
	 * <li>A QueuingThread is started, which tries and retries the Transporter's
	 * send method until either the specified number of tries are exhausted, or
	 * the message is successfully sent
	 * <li>The QueuingThread is returned
	 * </ol>
	 * 
	 * @param message
	 * @return Thread used to try to send message
	 * @throws IOException
	 */
	public QueuingThread send(TransportMessage message)
			throws TransportException {

		// find some way of selecting which transport method to use. - Either
		// read a default, or read a queue of methods or
		// whatever - but we should't be getting it from the Message.

		// ITransportMethod transportMethod = METHOD_STORE.getTransportMethod()
		return send(message, QueuingThread.DEFAULT_TRIES,
				QueuingThread.DEFAULT_DELAY);
	}

	/**
	 * 
	 * Send a message, specifying a number of tries and the pause between the
	 * tries (in seconds)
	 * 
	 * 
	 * @param message
	 * @param tries
	 * @param delay
	 * @return
	 * @throws IOException
	 */
	public QueuingThread send(TransportMessage message, int tries, int delay)
			throws TransportException {

		System.out.println("message: " + message);

		// create the appropriate transporter
		Transporter transporter = message.createTransporter();

		System.out.println("Transporter: " + transporter);

		// create a sender thread
		QueuingThread thread = new QueuingThread(transporter, MESSAGE_STORE,
				tries, delay);

		System.out.println("thread: " + thread);
		
		long deadline=getQueuingDeadline(thread.getTries(), thread
				.getDelay());

		System.out.println("deadline: " + deadline);
		
		// record the deadline for the queuing phase in the message
		message.setQueuingDeadline(deadline);

		// persist the message in the queue

		try {
			System.out.println("queing message ");
			MESSAGE_STORE.enqueue(message);
		} catch (IOException e) {
			throw new TransportException("Unable to queue message: "
					+ e.getMessage());
		}

		// start the queuing phase
		thread.start();

		// return the sender thread
		return thread;
	}

	private long getQueuingDeadline(int tries, int delay) {
		long deadline = (tries * delay * 1000);
		long now = new Date().getTime();
		return (deadline + now);

	}

	/**
	 * 
	 * Any messages which aren't successfully sent in QueuingThreads are then
	 * "Cached".
	 * 
	 * Applications can activate new attempts to send the CachedMessages via
	 * this sendCached method
	 * 
	 * @throws TransportException
	 * 
	 * 
	 */
	public void sendCached() throws TransportException {
		Vector messages = getCachedMessages();
		for (int i = 0; i < messages.size(); i++) {
			TransportMessage message = (TransportMessage) messages.elementAt(i);
			try {
				sendCachedMessage(message);
			} catch (IOException e) {
				e.printStackTrace();
				message.setFailureReason(e.getMessage());
				try {
					MESSAGE_STORE.updateMessage(message);
				} catch (IOException e1) {
					e1.printStackTrace();
					throw new TransportException(
							"Unable to persist change in message: "
									+ e1.getMessage());
				}
				throw new TransportException("Unable to send cached message: "
						+ e.getMessage());
			}
		}
	}

	/**
	 * 
	 * 
	 * Try to send one cached message, by creating a QueuingThread which will
	 * try just once
	 * 
	 * 
	 * @param message
	 * @throws IOException
	 */
	private void sendCachedMessage(TransportMessage message) throws IOException {
		// create the appropriate transporter
		Transporter transporter = message.createTransporter();
		// create a sender thread and start it
		new QueuingThread(transporter, MESSAGE_STORE, 1, 0).start();
	}

	/**
	 * @return
	 */
	public Vector getCachedMessages() {
		return MESSAGE_STORE.getCachedMessages();
	}

	/**
	 * @return
	 */
	public int getTransportQueueSize() {
		return MESSAGE_STORE.getTransportQueueSize();
	}

	/**
	 * 
	 * A TransportMessage is assigned a uniqueId when persisted. Applications
	 * can access the message again via this method
	 * 
	 * @param id
	 *            The unique id assigned to the TransportMessage when it was
	 *            queued for sending
	 * @return The TransportMessage identified by the id (or null if no such
	 *         message was found)
	 */
	public TransportMessage retrieve(String id) {
		return MESSAGE_STORE.findMessage(id);
	}

	public void registerTransportMethod(ITransportMethod transportMethod) {
		METHOD_STORE.register(transportMethod);
	}

}
