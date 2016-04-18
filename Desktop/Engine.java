import javax.swing.*;

import java.awt.*;
import java.awt.event.*;
import java.awt.image.ImageObserver;
import java.awt.image.ImageProducer;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.Random;

//http://www.tutorialspoint.com/java/java_thread_control.htm
//http://www.dreamincode.net/forums/topic/140116-solved-thanks-guysconcurrent-modification-exception/
//https://en.wikipedia.org/wiki/Momentum
//assume perfectly elastic collisions

public class Engine{
	public static double XFRAMESIZE = 1200;
	public static double YFRAMESIZE = 800;
	public static int LOOPS = 16;
	public static int nBalls = 50;
	public static final int[] sizeArray  = {Ball.SMALL, Ball.MEDIUM, Ball.LARGE};
	public static final Color[] colorArray = {Color.RED, Color.BLUE};
	
    public static void main(String[] args){
		final ArrayList<Ball> ballList = new ArrayList<Ball>(2*nBalls);
		//"safe" way to start gui
		EventQueue.invokeLater(new Runnable(){
			public void run(){
			    BallFrame gui = new BallFrame(ballList);
			    gui.setResizable(true);
			    gui.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
			    gui.setVisible(true);
			}
	    });	
    }
}

@SuppressWarnings("serial")
class BallFrame extends JFrame implements ActionListener{
	private final JButton setupBut = new JButton("setup");
	private final JButton startBut = new JButton("start");
    private final JButton pauseBut = new JButton("pause");	
    private final JButton stopBut = new JButton("stop");
    private final JButton addBut = new JButton("add");
    private final JButton removeBut = new JButton("rem");
    private final JLabel ballsNum = new JLabel("iterations");
    private final JPanel ballPanel;
    private final JPanel butPanel;
    private final JLabel threadLoad = new JLabel("threads");
    private final ArrayList<Ball> ballList;
    private AnimationThread anim;
    private boolean started;
    private boolean running;
    private boolean setupcheck;
    public static boolean requestAddBall;
    public static boolean requestRemBall;
    
    BallFrame(final ArrayList<Ball> ballList){
    	//initialize balls window
		this.ballList = ballList;
		started = false;
		running = false;
		setupcheck = false;
		requestAddBall = false;
		requestRemBall = false;
		setTitle("Bouncing Balls");
		setSize((int)Engine.XFRAMESIZE, (int)Engine.YFRAMESIZE);
		threadLoad.setText("Thread Load");
		ballsNum.setText("Balls: " + ballList.size() + ". Coll: 0. Same: 0. Eaten: 0. S: 0, M: 0, L: 0. Create: 0. Dest: 0. i: 0.");
		butPanel = new JPanel();
        JPanel threadPanel = new JPanel();
		ballPanel = new BallPanel(ballList);
		FlowLayout layout = new FlowLayout(FlowLayout.CENTER);
		butPanel.setLayout(layout);
		this.add(butPanel,BorderLayout.NORTH);
		this.add(ballPanel,BorderLayout.CENTER);
		this.add(threadPanel,BorderLayout.SOUTH);
		butPanel.add(setupBut);
		butPanel.add(startBut);
		butPanel.add(pauseBut);
		butPanel.add(stopBut);
		butPanel.add(addBut);
		butPanel.add(removeBut);
		butPanel.add(ballsNum);
		threadPanel.add(threadLoad);
		setupBut.addActionListener(this);
		startBut.addActionListener(this);
		pauseBut.addActionListener(this);
		addBut.addActionListener(this);
		stopBut.addActionListener(this);
		removeBut.addActionListener(this);
		
		//Recompute framesize static variables after resizing.
		addComponentListener(new ComponentAdapter() {

            @Override
            public void componentResized(ComponentEvent e) {
                Engine.XFRAMESIZE = e.getComponent().getBounds().getMaxX();
                Engine.YFRAMESIZE = e.getComponent().getBounds().getMaxY();
            }

            @Override
            public void componentMoved(ComponentEvent e) {
            }

            @Override
            public void componentShown(ComponentEvent e) {
            }

            @Override
            public void componentHidden(ComponentEvent e) {
            }
        });
    }    

    public void actionPerformed(ActionEvent ae){
    	if (ae.getSource() == setupBut){
    		if (!running && !setupcheck){
	    		setup();
	    		ballsNum.setText("Balls: " + ballList.size() + ". Coll: 0. Same: 0. Eaten: 0. S: 0, M: 0, L: 0. Create: 0. Dest: 0. i: 0.");
	    		setupcheck = true;
	    		repaint();
    		}
    	}
    	else if (ae.getSource() == startBut){
    		if (!started && setupcheck){
    			started = true;
    			running = true;
    			anim = new AnimationThread(ballPanel, ballsNum, threadLoad, ballList);
                Thread t = new Thread(anim);
    			t.start();
    		}
    	}
    	else if (ae.getSource() == pauseBut){
    		if (running && setupcheck){
	    		try{
	    			running = false;
	    			anim.suspend();
	    			pauseBut.setText("resume");
	    		}
	    		catch (Exception e){
	    		}
    		}
    		else if (!running && setupcheck){
    			try{
    				anim.resume();
    				pauseBut.setText("pause");
    				running = true;
    			}
    			catch (Exception e){
    			}
    		}
    	}
    	else if (ae.getSource() == stopBut){
    		if (started){
	    		started = false;
	    		running = false;
	    		anim.turnOff();
	    		stopBut.setText("reset");
    		}
    		else {
    			synchronized(ballList){
	    			ListIterator<Ball> li = ballList.listIterator();
		    		while(li.hasNext()) {
		    			li.next();
		    		    li.remove();
		    		}
    			}
	    		Engine.nBalls = 50;
	    		AnimationThread.resetCounts();
	    		threadLoad.setText("Thread Load");
	    		ballsNum.setText("Balls: " + ballList.size() + ". Coll: 0. Same: 0. Eaten: 0. S: 0, M: 0, L: 0. Create: 0. Dest: 0. i: 0.");
	    		stopBut.setText("stop");
	    		setupcheck = false;
	    		repaint();
    		}
    		
    	}
    	else if (ae.getSource() == addBut){
    		if (setupcheck && running){
	    		synchronized(this){
	    			requestAddBall = true;
	    		}
    		}
    	}
    	else if (ae.getSource() == removeBut){
    		if (setupcheck && running){
	    		synchronized(this){
	    			requestRemBall = true;
	    		}
    		}
    	}
    }
    
    void setup(){
    	//initialize ball list
		Random rn = new Random(); 
		for (int i=0 ; i < Engine.nBalls; ){
			
		    int ranSize = Engine.sizeArray[rn.nextInt(3)];
		    double ranX = rn.nextDouble()*Engine.XFRAMESIZE;
		    double ranY = rn.nextDouble()*Engine.YFRAMESIZE;
		    double ranU = rn.nextDouble();
		    double ranV = rn.nextDouble();
		    //Give possible negative velocity
		    if (((int)(ranU * 100.0))%2 == 0){
		    	ranU = -ranU;
		    }
		    if (((int)(ranV * 100.0))%2 == 0){
		    	ranV = -ranV;
		    }
		    Color ranColor = Engine.colorArray[rn.nextInt(2)];
		    Ball b = new Ball(ranSize,ranX,ranY,ranU,ranV,ranColor);
		    boolean overlap = false;
		    if (b.checkWall(ballPanel.getBounds())){
		    	continue;
		    }
		    for (Ball a: ballList){
		    	try{
			    	if (Ball.collides(a, b)){
			    		overlap = true;
			    		break;
			    	}
		    	}
		    	catch (NullPointerException e){}
		    }
		    
		    if (!overlap){
		    	synchronized(ballList){
		    		ballList.add(b);
		    	}
	    		i++;
		    }
		}
    }
}


@SuppressWarnings("serial")
class BallPanel extends JPanel{
	private final List<Ball> ballList;

    BallPanel(List<Ball> ballList){
    	this.ballList = ballList;
    }

    
    public void paintComponent(Graphics g){
		super.paintComponent(g);

		Graphics2D g2d = (Graphics2D)g;		
		g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
			    RenderingHints.VALUE_ANTIALIAS_ON);
		int minx = (int) this.getBounds().getX();
		int miny = (int) this.getBounds().getY();
		int maxx = (int) this.getBounds().getMaxX();
		int maxy = (int) this.getBounds().getMaxY();
		
		g2d.setColor(Color.black);
		g2d.fillRect(0, 0, 4, maxy - miny);
		g2d.fillRect(0, 0, maxx - minx, 4);
		g2d.fillRect(0, maxy - miny - 4, maxx - minx, 4);
		g2d.fillRect(maxx - 4, 0, 4, (maxy - miny) / 3);
		g2d.fillRect(maxx - 4, (maxy - miny) / 2, 4, (maxy - miny) / 2);
	
		synchronized(ballList){
			for (Ball b: ballList){
			    g2d.setColor(b.getColor());
		    	g2d.fill(b.getShape());	
			}
		}

    }    
}

class AnimationThread implements Runnable{
    private final JPanel ballPanel;
    private final JLabel ballsNum;
    private final JLabel threadLoad;
    private final List<Ball> ballList;
    private static int collisionCount = 0;
    private static int smallCollCount = 0;
    private static int medCollCount = 0;
    private static int largeCollCount = 0;
    private static int destCollCount = 0;
    private static int sameCollCount = 0;
    private static int createdCount = 0;
    private static int destroyedCount = 0;
    private boolean on = true;
    private boolean suspendFlag;
    private static final int THREADNUM = Runtime.getRuntime().availableProcessors();

    AnimationThread(JPanel ballPanel, JLabel ballsNum, JLabel threadLoad, List<Ball> ballList){
		this.suspendFlag = false;
    	this.ballPanel = ballPanel;
		this.ballList = ballList;
		this.ballsNum = ballsNum;
		this.threadLoad = threadLoad; 
    }

    public void run(){
    	Thread.currentThread().setPriority(Thread.MAX_PRIORITY);
    	int iter = 0;
		while (on){
			synchronized(this){
				while (suspendFlag){
					try {
						wait();
					} catch (InterruptedException e1) {}
				}
			}
		    int nBalls = ballList.size();
		    String output = "";
		    BallThread[] bts = new BallThread[THREADNUM];
		    for (int i = 0; i < THREADNUM; i++){
		    	if (i == THREADNUM - 1){
		    		bts[i] = new BallThread(ballList.subList(i*nBalls/THREADNUM, nBalls), ballPanel);
		    		output+="Thread " + Integer.toString(i + 1) + ": " + ballList.subList(i*nBalls/THREADNUM, nBalls).size();
		    	}
		    	else{
		    		bts[i] = new BallThread(ballList.subList(i*nBalls/THREADNUM, (i+1)*nBalls/THREADNUM), ballPanel);
		    		output+="Thread " + Integer.toString(i + 1) + ": " + ballList.subList(i*nBalls/THREADNUM, (i+1)*nBalls/THREADNUM).size() + ", ";
		    	}
		    	
		    }
		    
		    threadLoad.setText(output);
		    
//		    long start = System.nanoTime();
		    
		    for (BallThread b: bts){
		    	b.start();
		    }
		    for (BallThread b: bts){
		    	try{
		    		b.join();
		    	}
		    	catch(InterruptedException ie){
                }
		    }
		    
//		    long end = System.nanoTime();
//		    
//		    if (((end - start) / 1000000) > 100){
//		    	System.out.println("lag in ball threads:" + (end-start)/1000000 + ", iter: " + iter);
//		    }
		    
		    CollisionThread[] cts = new CollisionThread[THREADNUM];
		    for (int i = 0; i < THREADNUM; i++){
		    	if (i == THREADNUM - 1){
		    		cts[i] = new CollisionThread(ballList, ballList.subList(i*nBalls/THREADNUM, nBalls));
		    	}
		    	else{
		    		cts[i] = new CollisionThread(ballList, ballList.subList(i*nBalls/THREADNUM, (i+1)*nBalls/THREADNUM));
		    	}
		    	
		    }
		    
//		    start = System.nanoTime();
		    for (CollisionThread c: cts){
		    	c.start();
		    }
		    for (CollisionThread c: cts){
		    	try{
		    		c.join();
		    	}
		    	catch(InterruptedException ie){
                }
		    }
		    
//	    	end = System.nanoTime();
//		    
//		    if (((end - start) / 1000000) > 100){
//		    	System.out.println("lag in coll threads:" + (end-start)/1000000 + ", iter: " + iter);
//		    }
		    
		    List<Ball> removeList = new ArrayList<Ball>();
		    List<Ball> addList = new ArrayList<Ball>();
		    
//		    start = System.nanoTime();
		    
		    synchronized(ballList){
			    for (Ball b: ballList){
			    	if (!b.getCollisionList().isEmpty())
			    	{
			    		int result = b.updateBallCollision(removeList);
			    		
			    		if (result == -1){
			    			destCollCount++;
			    			collisionCount++;
			    		}
			    		else if (result == 4){
			    			sameCollCount++;
			    			collisionCount++;
			    		}
			    		else if (result == 1){
			    			smallCollCount++;
			    			collisionCount++;
			    			double rad = b.getRadius();
			    			double xcoll = ((b.getCollisionList().get(0).getX() + rad) + (b.getX() + rad)) / 2.0;
			    			double ycoll = ((b.getCollisionList().get(0).getY() + rad) + (b.getY() + rad)) / 2.0;
			    			addBall(addList, Engine.colorArray[0], Engine.sizeArray[0], xcoll, ycoll, rad);
			    		}
			    		else if (result == 2){
			    			medCollCount++;
			    			collisionCount++;
			    			double rad = b.getRadius();
			    			double xcoll = ((b.getCollisionList().get(0).getX() + rad) + (b.getX() + rad)) / 2.0;
			    			double ycoll = ((b.getCollisionList().get(0).getY() + rad) + (b.getY() + rad)) / 2.0;
			    			addBall(addList, Engine.colorArray[0], Engine.sizeArray[0], xcoll, ycoll, rad);
			    			addBall(addList, Engine.colorArray[0], Engine.sizeArray[0], xcoll, ycoll, rad);
			    		}
			    		else if (result == 3){
			    			largeCollCount++;
			    			collisionCount++;
			    			double rad = b.getRadius();
			    			double xcoll = ((b.getCollisionList().get(0).getX() + rad) + (b.getX() + rad)) / 2.0;
			    			double ycoll = ((b.getCollisionList().get(0).getY() + rad) + (b.getY() + rad)) / 2.0;
			    			addBall(addList, Engine.colorArray[0], Engine.sizeArray[0], xcoll, ycoll, rad);
			    			addBall(addList, Engine.colorArray[0], Engine.sizeArray[0], xcoll, ycoll, rad);
			    			addBall(addList, Engine.colorArray[1], Engine.sizeArray[0], xcoll, ycoll, rad);
			    		}
			    	}
			    }
		    }
		    
//		    end = System.nanoTime();
		    
//		    if (((end - start) / 1000000) > 100){
//		    	System.out.println("lag in animation thread:" + (end-start)/1000000 + ", iter: " + iter);
//		    }

		    ballsNum.setText("Balls: " + Integer.toString(Engine.nBalls) +". Coll: " 
		    		+ Integer.toString(collisionCount) +". Same: " + Integer.toString(sameCollCount)
			    		+". Eaten: " + Integer.toString(destCollCount) +". S: " + Integer.toString(smallCollCount) +", M: " 
				    		+ Integer.toString(medCollCount) +", L: " + Integer.toString(largeCollCount) + 
				    		". Create: " + Integer.toString(createdCount) + ". Dest: " + Integer.toString(destroyedCount) 
					    		+ ". i: " + Integer.toString(iter) + ".");
		    
		    if (collisionCount != sameCollCount + destCollCount + smallCollCount + medCollCount + largeCollCount){
		    	System.out.println("Sum doesn't match.");
		    }
		    
		    if (BallFrame.requestAddBall){
		    	addBall(addList);
		    	synchronized (this){
		    		BallFrame.requestAddBall = false;
		    	}
		    }
		    
		    if (BallFrame.requestRemBall){
		    	if (nBalls > 0){
		    		Random rand = new Random(System.currentTimeMillis());
			    	Ball b = ballList.get(rand.nextInt(Engine.nBalls));
			    	while (removeList.contains(b)){
			    		b = ballList.get(rand.nextInt(Engine.nBalls));
			    	}
			    	removeList.add(b);
		    	}
		    	synchronized (this){
		    		BallFrame.requestRemBall = false;
		    	}
		    }
		    
		    synchronized (ballList){
			    for (Ball b: ballList){
			    	if (b.getDestroy()){
			    		removeList.add(b);
			    	}
			    }
		    }
		    
		    for (Ball b: removeList){
		    	synchronized (ballList){
		    		ballList.remove(b);
		    	}
	    		destroyedCount++;
		    	Engine.nBalls--;
		    }

		    for (Ball b: addList){
		    	synchronized(ballList){
		    		ballList.add(b);
		    	}
		    	createdCount++;
		    	Engine.nBalls++;
		    }
	    	
		    ballPanel.repaint();
		    try{
		    	Thread.sleep(2);
		    }
		    catch (InterruptedException ie){
            }
			iter++;
		}
    }
    
    void addBall(List<Ball> addList){
    	Random rn = new Random(); 
	    double ranX = rn.nextDouble()*Engine.XFRAMESIZE;
	    double ranY = rn.nextDouble()*Engine.YFRAMESIZE;
	    double ranU = rn.nextDouble();
	    double ranV = rn.nextDouble();
	    int ballSize = Engine.sizeArray[rn.nextInt(3)];
	    Color ballColor = Engine.colorArray[rn.nextInt(2)];
	    //Give possible negative velocity
	    if (((int)(ranU * 100.0))%2 == 0){
	    	ranU = -ranU;
	    }
	    if (((int)(ranV * 100.0))%2 == 0){
	    	ranV = -ranV;
	    }
	    Ball n = new Ball(ballSize,ranX,ranY,ranU,ranV,ballColor);
	    int count = 0;
	    while (count != 1){
	    	boolean overlap = false;
	    	if (n.checkWall(ballPanel.getBounds())){
	    		ranX = rn.nextDouble()*Engine.XFRAMESIZE;
    		    ranY = rn.nextDouble()*Engine.YFRAMESIZE;
    		    n = new Ball(ballSize,ranX,ranY,ranU,ranV,ballColor);
    		    continue;
	    	}
		    for (Ball a: ballList){
		    	try{
			    	if (Ball.collides(a, n)){
			    		overlap = true;
			    		break;
			    	}
		    	}
		    	catch (NullPointerException e){}
		    }
		    
		    if (!overlap){
		    	for (Ball a: addList){
    		    	if (Ball.collides(a, n)){
    		    		overlap = true;
    		    		break;
    		    	}
    		    }
		    }
		    
		    if (overlap){
		    	ranX = rn.nextDouble()*Engine.XFRAMESIZE;
    		    ranY = rn.nextDouble()*Engine.YFRAMESIZE;
    		    n = new Ball(ballSize,ranX,ranY,ranU,ranV,ballColor);
		    }
		    else {
		    	addList.add(n);
		    	count++;
		    }
	    }
    }
    
    void addBall(List<Ball> addList, Color ballColor, int ballSize, double xcoll, double ycoll, double rcoll){
    	Random rn = new Random(); 
	    double ranX = xcoll;
	    double ranY = ycoll;
	    double ranU = rn.nextDouble();
	    double ranV = rn.nextDouble();
	    //Give possible negative velocity
	    if (((int)(ranU * 100.0)) % 2 == 0){
	    	ranU = -ranU;
	    }
	    if (((int)(ranV * 100.0)) % 2 == 0){
	    	ranV = -ranV;
	    }
	    Ball n = new Ball(ballSize,ranX,ranY,ranU,ranV,ballColor);
	    int count = 0;
	    double[] directionx = {-1.0, 0.0, 1.0, 1.0, 1.0, 0.0, -1.0, -1.0};
	    double[] directiony = {1.0, 1.0, 1.0, 0.0, -1.0, -1.0, -1.0, 0.0};
	    while (true && count < Engine.LOOPS){
	    	ranX = xcoll + ((count / 8) + 1) * rcoll*directionx[count%8];
		    ranY = ycoll + ((count / 8) + 1) * rcoll*directiony[count%8];
		    n = new Ball(ballSize,ranX,ranY,ranU,ranV,ballColor);
		    
	    	boolean overlap = false;
	    	if (n.checkWall(ballPanel.getBounds())){
    		    count++;
    		    continue;
	    	}
		    for (Ball a: ballList){
		    	try{
			    	if (Ball.collides(a, n)){
			    		overlap = true;
			    		break;
			    	}
		    	}
		    	catch (NullPointerException e){}
		    }
		    
		    if (!overlap){
		    	for (Ball a: addList){
    		    	if (Ball.collides(a, n)){
    		    		overlap = true;
    		    		break;
    		    	}
    		    }
		    }
		    
		    if (!overlap){
		    	addList.add(n);
		    	break;
		    }
		    count++;
	    }
	    
	    if (count >= Engine.LOOPS){
	    	addBall(addList, ballColor, ballSize);
	    }
    }
    
    void addBall(List<Ball> addList, Color ballColor, int ballSize){
    	Random rn = new Random(); 
	    double ranX = rn.nextDouble()*Engine.XFRAMESIZE;
	    double ranY = rn.nextDouble()*Engine.YFRAMESIZE;
	    double ranU = rn.nextDouble();
	    double ranV = rn.nextDouble();
	    //Give possible negative velocity
	    if (((int)(ranU * 100.0))%2 == 0){
	    	ranU = -ranU;
	    }
	    if (((int)(ranV * 100.0))%2 == 0){
	    	ranV = -ranV;
	    }
	    Ball n = new Ball(ballSize,ranX,ranY,ranU,ranV,ballColor);
	    int count = 0;
	    while (count != 1){
	    	boolean overlap = false;
	    	if (n.checkWall(ballPanel.getBounds())){
	    		ranX = rn.nextDouble()*Engine.XFRAMESIZE;
    		    ranY = rn.nextDouble()*Engine.YFRAMESIZE;
    		    n = new Ball(ballSize,ranX,ranY,ranU,ranV,ballColor);
    		    continue;
	    	}
	    	synchronized(ballList){
			    for (Ball a: ballList){
			    	try{
				    	if (Ball.collides(a, n)){
				    		overlap = true;
				    		break;
				    	}
			    	}
			    	catch (NullPointerException e){}
			    }
	    	}
		    if (!overlap){
		    	for (Ball a: addList){
		    		try{
	    		    	if (Ball.collides(a, n)){
	    		    		overlap = true;
	    		    		break;
	    		    	}
		    		}
		    		catch (NullPointerException e){}
    		    }
		    }
		    
		    if (overlap){
		    	ranX = rn.nextDouble()*Engine.XFRAMESIZE;
    		    ranY = rn.nextDouble()*Engine.YFRAMESIZE;
    		    n = new Ball(ballSize,ranX,ranY,ranU,ranV,ballColor);
		    }
		    else {
		    	addList.add(n);
		    	count++;
		    }
	    }
    }
    
    void suspend(){
    	suspendFlag = true;
    }
    
    synchronized void resume(){
    	suspendFlag = false;
    	notify();
    }
    
    void turnOff(){
		this.on = false;
	}

	public static void resetCounts(){
    	collisionCount = 0;
        smallCollCount = 0;
        medCollCount = 0;
        largeCollCount = 0;
        destCollCount = 0;
        sameCollCount = 0;
        createdCount = 0;
        destroyedCount = 0;
    }

}

class BallThread extends Thread{
    private final List<Ball> ballList;
    private final JPanel ballPanel;
    
    BallThread(List<Ball> ballList,JPanel ballPanel){
		this.ballList = ballList;
		this.ballPanel = ballPanel;
    }

    public void run(){
    	Thread.currentThread().setPriority(Thread.MAX_PRIORITY);
		for (Ball b:ballList){
		    b.updatePosition(ballPanel.getBounds());
		}
    }
}

class CollisionThread extends Thread{
    private final List<Ball> ballList;
    private final List<Ball> subList;
    
    CollisionThread(List<Ball> ballList, List<Ball> subList){
		this.ballList = ballList;
		this.subList = subList;
    }

    public void run(){
    	Thread.currentThread().setPriority(Thread.MAX_PRIORITY);
    	try{
			for (Ball b:subList){
			   	b.updateCollisionList(ballList);
			}
    	}
    	catch (NullPointerException e){
    	}
    }
}

