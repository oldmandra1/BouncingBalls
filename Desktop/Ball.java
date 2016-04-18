import java.util.List;
import java.util.ArrayList;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;
import java.awt.Color;

//http://www.hoomanr.com/Demos/Elastic2/ for ball physics

class Ball {
    private final int size;
    private double x,y;
    private final Color color;
    private double diameter;
    private final double m;
    private double dx;
    private double dy;
    private double dxp;
    private double dyp;
    private double vel;
    private final ArrayList<Ball> collisionList;
    private static Ball scrapBall = new Ball(0, -50, -50, 0, 0, Engine.colorArray[0]);;
    private Ball recentBall;
    private boolean postCollision;
    private boolean exiting;
    private boolean destroy;

    public static final int SMALL=0;
    public static final int MEDIUM=1;
    public static final int LARGE=2;

    Ball(int size, double xpos, double ypos,
	 double xvel, double yvel, Color color){
		this.size = size;
		this.x = xpos;
		this.y = ypos;
		this.dx = xvel;
		this.dy = yvel;
		this.dxp = dx;
		this.dyp = dy;
		this.color = color;
		this.postCollision = false;
		this.exiting = false;
		this.destroy = false;
		
		if (size == SMALL)
		    diameter = 15.0;
		else if (size == MEDIUM)
		    diameter = 20.0;
		else if (size == LARGE)
		    diameter = 25.0;
		
		//calculate mass
		this.m = diameter * diameter;
		//calculate velocity
		this.vel = calculateVelocity(dx, dy);
		
		collisionList = new ArrayList<Ball>(3);
		this.recentBall = scrapBall;
    }

    public void updatePosition(Rectangle2D bounds){
    	collisionList.clear(); 

		x += dx;
		y += dy;
		
		double xmax = bounds.getMaxX() - 4;
		double ymin = (bounds.getMaxY() - bounds.getMinY()) / 3;
		double ymax = (bounds.getMaxY() - bounds.getMinY()) / 2;
		
		if (exiting){			
			if (x >= xmax){
				this.destroy = true;
			}
			
			if (y <= ymin || y + diameter >= ymax){
				dy = -dy;
			}
			
			if (dx < 0){
				exiting = false;
			}
			
			return;
		}
		//check if ball is going out the window
		if (x + diameter >= xmax && (y >= ymin && y + diameter <= ymax)){
			exiting = true;
			return;
		}
		
		//check wall boundary
		if (x - 4.0 < 0.0){ 
			x = 4.0;
			dx = -dx;
	    }
		if (x + diameter + 4.0 > bounds.getMaxX()){
			x = bounds.getMaxX() - diameter - 4.0; 
			dx = -dx; 
    	}
		if (y - 4.0 < 0){
			y = 4.0 ; 
			dy = -dy;
		}
		if (y + diameter + 4.0 > bounds.getMaxY() - bounds.getY()){
			y = bounds.getMaxY() - bounds.getY() - diameter - 4.0;
			dy = -dy; 
	    }
		exiting = false;
		postCollision = false;
    }
    
    public void updateCollisionList(List<Ball> ballList) throws NullPointerException{
    	//check collisions
    	try{
	    	for (Ball b: ballList){
	    		if (this == b || b == recentBall){
	    			continue;
	    		}
	    		if (collides(this, b)){
	    			collisionList.add(b);
	    		}
	    	}
	    	
	    	if (!collides(this, recentBall)){
	    		recentBall = scrapBall;
	    	}
    	}
    	catch (NullPointerException e){
    		throw e;
    	}
    }
    
    //returns -1  if a ball is being removed
	//returns 0 if ball collision has already been handled
	//returns 1 if two small balls of same color are interacting
	//returns 2 if two medium balls of same color are interacting
	//returns 3 if two large balls of same color are interacting
	//return 4 if balls are same color
	public int updateBallCollision(List<Ball> removeList){
		if (postCollision){
			return 0;
		}
		
		//pick first collision ball
		Ball cb = collisionList.get(0);
		recentBall = cb;
		
		//test color first
		if (color != cb.color){
			if (size > cb.size){
				postCollision = true;
				cb.postCollision = true;
				removeList.add(cb);
				return -1;
			}
			else if (size < cb.size) {
				postCollision = true;
				cb.postCollision = true;
				removeList.add(this);
				return -1;
			}
		}
		
		//store all values in previous elements
		dxp = dx;
		dyp = dy;
		
		if (!cb.postCollision){
	    	cb.dxp = cb.dx;
	    	cb.dyp = cb.dy;
		}
		
		//calculate angle of each ball
		double bang = Math.atan2(dy, dx);
		double cbang = Math.atan2(cb.dyp, cb.dxp);
		
		//calculate angle of impact
		double impang = calculateAngle(this, cb);
		
		//calculate velocity of each ball
		this.vel = calculateVelocity(dx, dy);
		double cbv = calculateVelocity(cb.dxp, cb.dyp);
		
		double bv = this.vel;
		
		//calculate velocities on tangent and perpendicular to tangent
		double bvx = bv * Math.cos(bang - impang);
		double bvy = bv * Math.sin(bang - impang);
		double cbvx = cbv * Math.cos(cbang - impang);
		double cbvy = cbv * Math.sin(cbang - impang);
		
		//calculate new velocities after conservation of momentum on tangent
		double bvx2 = (bvx * (m - cb.m) + 2 * cb.m * cbvx) / (m + cb.m);
		double cbvx2 = (cbvx * (cb.m - m) + 2 * m * bvx) / (m + cb.m);
		
		this.vel = calculateVelocity(bvx2, bvy);
		cbv = calculateVelocity(cbvx2, cbvy);
	
		bv = this.vel;
		
		//direction
		bang = Math.atan2(bvy, bvx2) + impang;
		cbang = Math.atan2(cbvy, cbvx2) + impang;
		
		//calculate final x and y
		dx = bv * Math.cos(bang);
		dy = bv * Math.sin(bang);
		
		if (!cb.postCollision){
			cb.vel = cbv;
			cb.dx = cbv * Math.cos(cbang);
			cb.dy = cbv * Math.sin(cbang);
		}
		
		postCollision = true;
		cb.postCollision = true;
		
		if (this.color != cb.color){
			if (this.size == 0){
				return 1;
			}
			else if (this.size == 1){
				return 2;
			}
			else if (this.size == 2){ 
				return 3;
			}
		}
	
		return 4;
	}

	//This NullPointerException occurs only very rarely.  There has to be so many balls in the window (usually the result of vicious cycle of
	//ball multiplication, that a collision thread is still trying to process collision detection when the user hits the stop and reset buttons.
	//This will safely handle that error so that the simulation can be restarted.
	public static boolean collides(Ball a, Ball b) throws NullPointerException{
		try{
			if (a.x == b.x && a.y == b.y) return true;
		}
		catch (NullPointerException e){
			throw e;
		}
		double distx = (a.x + a.diameter / 2.0) - (b.x + b.diameter / 2.0);
		double disty = (a.y + a.diameter / 2.0) - (b.y + b.diameter / 2.0);
		double dist = Math.sqrt(distx*distx + disty*disty);
		return (dist <= (b.diameter + a.diameter) / 2.0);
    }
    
    public boolean checkWall(Rectangle2D bounds){
    	if (x - 4.0 < 0){ 
			return true;
	    }
		if (x + diameter + 4.0 > bounds.getMaxX()){
			return true;
    	}
        return y - 4.0 < 0 || y + diameter + 4.0 > bounds.getMaxY() - bounds.getY();

    }
    
    double calculateAngle(Ball a, Ball b){
    	double distx = (a.x + a.diameter / 2.0) - (b.x + b.diameter / 2.0);
		double disty = (a.y + a.diameter / 2.0) - (b.y + b.diameter / 2.0);
		return Math.atan2(disty, distx);
    }
        
    private static double calculateVelocity(double dx, double dy){
    	return Math.sqrt(dx*dx+dy*dy);
    }

    public Color getColor(){
    	return(this.color);
    }


    public Ellipse2D getShape(){
    	return new Ellipse2D.Double(x,y,diameter,diameter);
    }

	public boolean getDestroy(){
		return this.destroy;
	}

	public ArrayList<Ball> getCollisionList(){
		return this.collisionList;
	}
	
	public double getRadius(){
		return this.diameter / 2.0;
	}

	public double getX(){
		return this.x;
	}

	public double getY(){
		return this.y;
	}
}
