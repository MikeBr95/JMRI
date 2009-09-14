package jmri.implementation;

import jmri.*;
import jmri.Light;
import jmri.Conditional;
import jmri.Sensor;
import jmri.Turnout;
import jmri.SignalHead;
import jmri.Memory;
import java.util.ArrayList;
import java.util.Iterator;

 /**
 * Class providing the basic logic of the Logix interface.
 *
 * @author	Dave Duchamp Copyright (C) 2007
 * @version     $Revision: 1.4 $
 * @author Pete Cressman Copyright (C) 2009
 */
public class DefaultLogix extends AbstractNamedBean
    implements Logix, java.io.Serializable {

    public DefaultLogix(String systemName, String userName) {
        super(systemName, userName);
    }

    public DefaultLogix(String systemName) {
        super(systemName);
    }

    /**
     *  Persistant instance variables (saved between runs)
     */
    ArrayList <String> _conditionalSystemNames = new ArrayList<String>();
    ArrayList <JmriSimplePropertyListener> _listeners = new ArrayList<JmriSimplePropertyListener>();

    /**
     *  Operational instance variables (not saved between runs)
     */
	private boolean mEnabled = true;
	
	private boolean _isActivated = false; 
		
	/**
	 * Get number of Conditionals for this Logix
	 */
	public int getNumConditionals() {
		return _conditionalSystemNames.size();
	}

	/**
	 * Move 'row' to 'nextInOrder' and shift all between 'row' and 'nextInOrder'
     * up one position   ( row > nextInOrder )
	 */
    public void swapConditional(int nextInOrder, int row) {
        if (row <= nextInOrder) {
            return;
        }
        String temp = _conditionalSystemNames.get(row);
        for (int i = row; i > nextInOrder; i--)
        {
            _conditionalSystemNames.set(i, _conditionalSystemNames.get(i-1));
        }
        _conditionalSystemNames.set(nextInOrder, temp);
    }
	
	/**
	 * Returns the system name of the conditional that will calculate in the
	 * specified order. This is also the order the Conditional is listed in
	 * the Add/Edit Logix dialog.
	 * If 'order' is greater than the number of Conditionals for this Logix,
	 * and empty String is returned.
	 * @param order - order in which the Conditional calculates.
	 */	
	public String getConditionalByNumberOrder(int order) {
        try {
            return _conditionalSystemNames.get(order);
        }
        catch (java.lang.IndexOutOfBoundsException ioob)
        {
            return null;
        }
	}
	
	/**
     * Add a Conditional to this Logix
	 * Returns true if Conditional was successfully added, returns false
	 * if the maximum number of conditionals has been exceeded.
     * @param systemName The Conditional system name
	 * @param order - the order this conditional should calculate in
	 *                 if order is negative, the conditional is added
	 *				   at the end of current group of conditionals
     */
    public boolean addConditional(String systemName,int order) {
        _conditionalSystemNames.add(systemName);
		return (true);
	}

    /**
     * Set enabled status.  Enabled is a bound property
	 *   All conditionals are set to UNKNOWN state and recalculated
	 *		when the Logix is enabled, provided the Logix has been 
	 *		previously activated.
     */
    public void setEnabled(boolean state) {

        boolean old = mEnabled;
        mEnabled = state;
        for (int i=_listeners.size()-1; i>=0; i--)
        {
            _listeners.get(i).setEnabled(state);
        }
        if (old != state) {
			firePropertyChange("Enabled", new Boolean(old), new Boolean(state));
			// set the state of all Conditionals to UNKNOWN
			if ( _isActivated ) {
				Conditional c = null;
				for (int i=0; i<_conditionalSystemNames.size(); i++) {
					c = InstanceManager.conditionalManagerInstance().
										getBySystemName(_conditionalSystemNames.get(i));
					if (c!=null) {
                        try {
                            c.setState(Conditional.UNKNOWN);
                        } catch ( JmriException e) {
                              // ignore
                        }
					}
				}
				calculateConditionals();
			}
		}
    }

    /**
     * Get enabled status
    */
    public boolean getEnabled() { 
		return mEnabled; 
	}

	/**
     * Delete a Conditional and remove it from this Logix
     * <P>
	 * Note: Since each Logix must have at least one Conditional to
	 *	do anything, the user is warned in Logix Table Action when the 
	 *  last Conditional is deleted.
     * <P>
	 * Returns true if Conditional was successfully deleted, otherwise 
	 *  returns false. 
     * @param systemName The Conditional system name
     */
    public String[] deleteConditional(String systemName) {
		if (_conditionalSystemNames.size()<=0) {
			return (null);
		}
        // check other Logix(es) for use of this conditional (systemName) for use as a
        // variable in one of their conditionals
        Iterator<String> iter1 = InstanceManager.logixManagerInstance().getSystemNameList().iterator();
        while (iter1.hasNext()) {
            String sNameLogix = iter1.next();
            if (!sNameLogix.equals(getSystemName()) ) {
                Logix x = InstanceManager.logixManagerInstance().getBySystemName(sNameLogix);
                int numCond = x.getNumConditionals();
                for (int i=0; i<numCond; i++) {
                    String sNameCond = x.getConditionalByNumberOrder(i);
                    Conditional c = InstanceManager.conditionalManagerInstance().getBySystemName(sNameCond);
                    ArrayList <ConditionalVariable> varList = c.getCopyOfStateVariables();
                    for (int k=0; k<varList.size(); k++)  {
                        ConditionalVariable v = varList.get(k);
                        if ( (v.getType()==Conditional.TYPE_CONDITIONAL_TRUE) || 
                             (v.getType()==Conditional.TYPE_CONDITIONAL_FALSE) )
                        {
                            String name = v.getName();
                            Conditional c1 = InstanceManager.conditionalManagerInstance().getConditional(name);
                            if (c1 == null) {
                                log.error("\""+name+"\" is a non-existent Conditional variable in Conditional \""
                                          +c.getUserName()+"\" in Logix \""+x.getUserName()+"\" ("+sNameLogix+")");
                            } else {
                                if ( systemName.equals(c1.getSystemName()) ) {
                                    String[] result = new String[] {name, systemName, c.getUserName(),
                                                            sNameCond, x.getUserName(), sNameLogix};
                                    return result;
                                }
                            }
                        }
                    }
                }
            }
        }
		// Remove Conditional from this logix
        if (!_conditionalSystemNames.remove(systemName)) {
			log.error("attempt to delete Conditional not in Logix: "+systemName);
            return null;
        }
		// delete the Conditional object
		Conditional c = InstanceManager.conditionalManagerInstance().getBySystemName(systemName);
		if (c == null) {
			log.error("attempt to delete non-existant Conditional - "+systemName);
            return null;
		}
		InstanceManager.conditionalManagerInstance().deleteConditional(c);
		return (null);
	}	
	
    /**
	 * Calculate all Conditionals, triggering action if the user specified
	 *   conditions are met, and the Logix is enabled.
	 */
	public void calculateConditionals() {
		// are there Conditionals to calculate?
			// There are conditionals to calculate
        String cName = "";
        Conditional c = null;
        for (int i=0; i<_conditionalSystemNames.size(); i++) {
            cName = _conditionalSystemNames.get(i);
            c = InstanceManager.conditionalManagerInstance().getBySystemName(cName);
            if (c==null) {
                log.error("Invalid conditional system name when calculating Logix - "+cName);
            }
            else {
                // calculate without taking any action unless Logix is enabled
                c.calculate(mEnabled, null);
            }
        }
	}
	
    /**
     * Activate the Logix, starts Logix processing by connecting all
	 *    inputs that are included the Conditionals in this Logix.
     * <P>
     * A Logix must be activated before it will calculate any of its
	 *    Conditionals.
     */
    @SuppressWarnings("deprecation")
    public void activateLogix() {
		// if the Logix is already busy, simply return
		if (_isActivated) return;
		// set the state of all Conditionals to UNKNOWN
        ConditionalManager cm = InstanceManager.conditionalManagerInstance();
        for (int i=0; i<_conditionalSystemNames.size(); i++) {
            Conditional conditional = cm.getBySystemName(_conditionalSystemNames.get(i));
            if (conditional!=null) {
                try {
                    conditional.setState(Conditional.UNKNOWN);
                } catch ( JmriException e) {
                      // ignore
                }
            }
        }
		// assemble a list of needed listeners
		assembleListenerList();
		// create and attach the needed property change listeners
		// start a minute Listener if needed
        for (int i=0; i<_listeners.size(); i++) {
            startListener(_listeners.get(i));
        }
		// mark this Logix as busy
		_isActivated = true;
		// calculate this Logix to set initial state of Conditionals
		calculateConditionals();
	}
	
	/**
	 * Assembles a list of Listeners needed to activate this Logix
	 */
	private void assembleListenerList() {
		// initialize
        for (int i=_listeners.size()-1; i>=0; i--)
        {
            removeListener(_listeners.get(i));
        }
		_listeners = new ArrayList<JmriSimplePropertyListener>();
		// cycle thru Conditionals to find objects to listen to
        ConditionalManager cm = InstanceManager.conditionalManagerInstance();
		for (int i=0; i<_conditionalSystemNames.size(); i++) {
            Conditional conditional = null;
			conditional = cm.getBySystemName(_conditionalSystemNames.get(i));
			if (conditional!=null) {
                ArrayList<ConditionalVariable> variableList = conditional.getCopyOfStateVariables();
                for (int k = 0; k<variableList.size(); k++) {
                    ConditionalVariable variable = variableList.get(k);
                    // check if listening for a change has been suppressed
                    int varListenerType = 0;
                    String varName = variable.getName();
                    int varType = variable.getType();
                    int signalAspect = -1;
                    boolean newSV = true;
                    // Get Listener type from varible type
                    switch(varType) {
                        case Conditional.TYPE_SENSOR_ACTIVE:
                        case Conditional.TYPE_SENSOR_INACTIVE:
                            varListenerType = LISTENER_TYPE_SENSOR;
                            break;
                        case Conditional.TYPE_TURNOUT_THROWN:
                        case Conditional.TYPE_TURNOUT_CLOSED:
                            varListenerType = LISTENER_TYPE_TURNOUT;
                            break;
                        case Conditional.TYPE_CONDITIONAL_TRUE:
                        case Conditional.TYPE_CONDITIONAL_FALSE:
                            varListenerType = LISTENER_TYPE_CONDITIONAL;
                            break;
                        case Conditional.TYPE_LIGHT_ON:
                        case Conditional.TYPE_LIGHT_OFF:
                            varListenerType = LISTENER_TYPE_LIGHT;
                            break;
                        case Conditional.TYPE_MEMORY_EQUALS:
                        case Conditional.TYPE_MEMORY_COMPARE:
                            varListenerType = LISTENER_TYPE_MEMORY;
                            break;
                        case Conditional.TYPE_FAST_CLOCK_RANGE:
                            varListenerType = LISTENER_TYPE_FASTCLOCK;
                            varName = "clock";
                            break;
                        case Conditional.TYPE_SIGNAL_HEAD_RED:
                            varListenerType = LISTENER_TYPE_SIGNAL;
                            signalAspect = SignalHead.RED;
                            break;
                        case Conditional.TYPE_SIGNAL_HEAD_YELLOW:
                            varListenerType = LISTENER_TYPE_SIGNAL;
                            signalAspect = SignalHead.YELLOW;
                            break;
                        case Conditional.TYPE_SIGNAL_HEAD_GREEN:
                            varListenerType = LISTENER_TYPE_SIGNAL;
                            signalAspect = SignalHead.GREEN;
                            break;
                        case Conditional.TYPE_SIGNAL_HEAD_DARK:
                            varListenerType = LISTENER_TYPE_SIGNAL;
                            signalAspect = SignalHead.DARK;
                            break;
                        case Conditional.TYPE_SIGNAL_HEAD_FLASHRED:
                            varListenerType = LISTENER_TYPE_SIGNAL;
                            signalAspect = SignalHead.FLASHRED;
                            break;
                        case Conditional.TYPE_SIGNAL_HEAD_FLASHYELLOW:
                            varListenerType = LISTENER_TYPE_SIGNAL;
                            signalAspect = SignalHead.FLASHYELLOW;
                            break;
                        case Conditional.TYPE_SIGNAL_HEAD_FLASHGREEN:
                            varListenerType = LISTENER_TYPE_SIGNAL;
                            signalAspect = SignalHead.FLASHGREEN;
                            break;
                        case Conditional.TYPE_SIGNAL_HEAD_LIT:
                        case Conditional.TYPE_SIGNAL_HEAD_HELD:
                            varListenerType = LISTENER_TYPE_SIGNAL;
                            break;
                    }
                    newSV = true;
                        // check if already in list
                    int positionOfListener = -1;
                    for (int j=0; (j<_listeners.size()); j++) {
                        if (varListenerType==_listeners.get(j).getType() ) {
                            if (varName.equals(_listeners.get(j).getDevName())) {
                                if (varListenerType == LISTENER_TYPE_SIGNAL) {
                                    if (varType == Conditional.TYPE_SIGNAL_HEAD_LIT || 
                                                varType == Conditional.TYPE_SIGNAL_HEAD_HELD ) {
                                        if (varType == _listeners.get(j).getVarType() ) {
                                            positionOfListener = j;
                                            newSV = false;
                                            break;
                                        }
                                    } else if ("Appearance".equals(_listeners.get(j).getPropertyName())) {
                                            // the Appearance Listener can handle all aspects
                                        positionOfListener = j;
                                        newSV = false;
                                        break;
                                    }
                                } else {
                                    positionOfListener = j;
                                    newSV = false;
                                    break;
                                }
                            }
                        }

                    }
                    // add to list if new
                    JmriSimplePropertyListener listener = null;
                    if (newSV) {
                        switch (varListenerType) {
                            case LISTENER_TYPE_SENSOR:
                                listener = new JmriSimplePropertyListener("KnownState", LISTENER_TYPE_SENSOR, 
                                                                    varName, varType, conditional);
                                break;
                            case LISTENER_TYPE_TURNOUT:
                                listener = new JmriSimplePropertyListener("KnownState", LISTENER_TYPE_TURNOUT, 
                                                                    varName, varType, conditional);
                                break;
                            case LISTENER_TYPE_CONDITIONAL:
                                listener = new JmriSimplePropertyListener("KnownState", LISTENER_TYPE_CONDITIONAL, 
                                                                    varName, varType, conditional);
                                break;
                            case LISTENER_TYPE_LIGHT:
                                listener = new JmriSimplePropertyListener("KnownState", LISTENER_TYPE_LIGHT,
                                                                    varName, varType, conditional);
                                break;
                            case LISTENER_TYPE_MEMORY:
                                listener = new JmriSimplePropertyListener("value", LISTENER_TYPE_MEMORY, 
                                                                          varName, varType, conditional);
                                break;
                            case LISTENER_TYPE_FASTCLOCK:
                                listener = new JmriClockPropertyListener("minutes", LISTENER_TYPE_FASTCLOCK, 
                                                                         varName, varType, conditional,
                                                                    variable.getNum1(), variable.getNum2());
                                break;
                            case LISTENER_TYPE_SIGNAL:
                                if (signalAspect <0) {
                                    if (varType == Conditional.TYPE_SIGNAL_HEAD_LIT) {
                                        listener = new JmriSimplePropertyListener("Lit", LISTENER_TYPE_SIGNAL,
                                                                            varName, varType, conditional);
                                    } else { // varType == Conditional.TYPE_SIGNAL_HEAD_HELD
                                        listener = new JmriSimplePropertyListener("Held", LISTENER_TYPE_SIGNAL,
                                                                            varName, varType, conditional);
                                    }
                                } else {
                                    listener = new JmriMultiStatePropertyListener("Appearance", LISTENER_TYPE_SIGNAL,
                                                                        varName, varType, conditional, signalAspect);
                                }
                                break;
                            default:
                                log.error("Unknown (new) Variable Listener type= "+varListenerType+", for varName= "
                                          +varName+", varType= "+varType+" in Conditional, "+
                                          _conditionalSystemNames.get(i));
                                continue;
                        }
                        _listeners.add(listener);
                        //log.debug("Add listener for "+varName);
                    }
                    else {
                        switch (varListenerType) {
                            case LISTENER_TYPE_SENSOR:
                            case LISTENER_TYPE_TURNOUT:
                            case LISTENER_TYPE_CONDITIONAL:
                            case LISTENER_TYPE_LIGHT:
                            case LISTENER_TYPE_MEMORY:
                                listener = _listeners.get(positionOfListener);
                                listener.addConditional(conditional);
                                break;
                            case LISTENER_TYPE_FASTCLOCK:
                                JmriClockPropertyListener cpl = 
                                        (JmriClockPropertyListener)_listeners.get(positionOfListener);
                                cpl.setRange(variable.getNum1(), variable.getNum2());
                                cpl.addConditional(conditional);
                                break;
                            case LISTENER_TYPE_SIGNAL:
                                if (signalAspect < 0) {
                                    listener = _listeners.get(positionOfListener);
                                    listener.addConditional(conditional);
                                } else {
                                    JmriMultiStatePropertyListener mpl = 
                                        (JmriMultiStatePropertyListener)_listeners.get(positionOfListener);
                                    mpl.addConditional(conditional);
                                    mpl.setState(signalAspect);
                                }
                                break;
                            default:
                                log.error("Unknown (old) Variable Listener type= "+varListenerType+", for varName= "
                                          +varName+", varType= "+varType+" in Conditional, "+
                                          _conditionalSystemNames.get(i));
                        }
                    }
				}
			}
			else {
				log.error("invalid conditional system name in Logix \""+getSystemName()+
                          "\" assembleListenerList DELETING "+
				          _conditionalSystemNames.get(i)+ " from Conditional list." );
                _conditionalSystemNames.remove(i);
                
			}
		}
	}
	
	/**
	 * Assembles and returns a list of state variables that are used by conditionals 
	 *   of this Logix including the number of occurances of each variable that 
	 *   trigger a calculation, and the number of occurances where the triggering 
	 *   has been suppressed.
	 * The main use of this method is to return information that can be used to test 
	 *   for inconsistency in suppressing triggering of a calculation among multiple 
	 *   occurances of the same state variable.
     * Caller provides an ArrayList of the variables to check and and empty Array list
     *   to return the counts for triggering or suppressing calculation.  The first 
     *   index is a count that the correspondeing variable triggers calculation and
     *   second is a count that the correspondeing variable suppresses Calculation.
     * Note this method must not modify the supplied variable list in any way.
	 *
	public void getStateVariableList(ArrayList <ConditionalVariable> varList, ArrayList <int[]> triggerPair) {  
		// initialize
		Conditional c = null;
		String testSystemName = "";
		String testUserName = "";
		String testVarName = "";
		// cycle thru Conditionals to find state variables
        ConditionalManager cm = InstanceManager.conditionalManagerInstance();
		for (int i=0; i<_conditionalSystemNames.size(); i++) {
			c = cm.getBySystemName(_conditionalSystemNames.get(i));
			if (c!=null) {
                ArrayList variableList = c.getCopyOfStateVariables();
                for (int k = 0; k<variableList.size(); k++) {
                    ConditionalVariable variable = (ConditionalVariable)variableList.get(k);
                    testVarName = variable.getName();
                    testSystemName = "";
                    testUserName = "";
                    // initialize this state variable
                    switch (variable.getType()) {
                        case Conditional.TYPE_SENSOR_ACTIVE:
                        case Conditional.TYPE_SENSOR_INACTIVE:
                            Sensor s = InstanceManager.sensorManagerInstance().
                                                getSensor(testVarName);
                            if (s!=null) {
                                testSystemName = s.getSystemName();
                                testUserName = s.getUserName();
                            }
                            break;
                        case Conditional.TYPE_TURNOUT_THROWN:
                        case Conditional.TYPE_TURNOUT_CLOSED:
                            Turnout t = InstanceManager.turnoutManagerInstance().
                                                getTurnout(testVarName);
                            if (t!=null) {
                                testSystemName = t.getSystemName();
                                testUserName = t.getUserName();
                            }
                            break;
                        case Conditional.TYPE_CONDITIONAL_TRUE:
                        case Conditional.TYPE_CONDITIONAL_FALSE:
                            Conditional cx = InstanceManager.conditionalManagerInstance().
                                                getConditional(this,testVarName);
                            if (cx==null) {
                                cx = InstanceManager.conditionalManagerInstance().
                                                getBySystemName(testVarName);
                            }
                            if (cx!=null) {
                                testSystemName = cx.getSystemName();
                                testUserName = cx.getUserName();
                            }
                            break;
                        case Conditional.TYPE_LIGHT_ON:
                        case Conditional.TYPE_LIGHT_OFF:
                            Light lgt = InstanceManager.lightManagerInstance().
                                                getLight(testVarName);
                            if (lgt!=null) {
                                testSystemName = lgt.getSystemName();
                                testUserName = lgt.getUserName();
                            }
                            break;
                        case Conditional.TYPE_MEMORY_EQUALS:
                            Memory m = InstanceManager.memoryManagerInstance().
                                                getMemory(testVarName);
                            if (m!=null) {
                                testSystemName = m.getSystemName();
                                testUserName = m.getUserName();
                            }
                            break;
                        case Conditional.TYPE_SIGNAL_HEAD_RED:
                        case Conditional.TYPE_SIGNAL_HEAD_YELLOW:
                        case Conditional.TYPE_SIGNAL_HEAD_GREEN:
                        case Conditional.TYPE_SIGNAL_HEAD_DARK:
                        case Conditional.TYPE_SIGNAL_HEAD_FLASHRED:
                        case Conditional.TYPE_SIGNAL_HEAD_FLASHYELLOW:
                        case Conditional.TYPE_SIGNAL_HEAD_FLASHGREEN:
                            SignalHead h = InstanceManager.signalHeadManagerInstance().
                                                getSignalHead(testVarName);
                            if (h!=null) {
                                testSystemName = h.getSystemName();
                                testUserName = h.getUserName();
                            }
                            break;
                        case Conditional.TYPE_SIGNAL_HEAD_LIT:
                            SignalHead hx = InstanceManager.signalHeadManagerInstance().
                                                getSignalHead(testVarName);
                            if (hx!=null) {
                                testSystemName = hx.getSystemName();
                                testUserName = hx.getUserName();
                            }
                            break;
                        case Conditional.TYPE_SIGNAL_HEAD_HELD:
                            SignalHead hy = InstanceManager.signalHeadManagerInstance().
                                                getSignalHead(testVarName);
                            if (hy!=null) {
                                testSystemName = hy.getSystemName();
                                testUserName = hy.getUserName();
                            }
                            break;
                        default:
                            testSystemName = "";
                    }
                    // check if this state variable is already in the list to be returned
                    boolean inList = false;
                    int indexOfRepeat = -1;
                    if (testSystemName!="") {
                        // getXXXXXX succeeded, process this state variable
                        for (int j=0; j<varList.size(); j++)  {
                            ConditionalVariable v = varList.get(j);
                            if ( v.getName().equals(testSystemName) || v.getName().equals(testUserName) ) {
                                inList = true;
                                indexOfRepeat = j;
                                break;
                            }
                        }
                        // add to list if new and if there is room
                        if ( inList ) {
                            int[] trigs = triggerPair.get(indexOfRepeat);
                            if ( variable.doCalculation() ) {
                                trigs[0]++;
                            }
                            else {
                                trigs[1]++;

                            }
                        }
                    }
                }
			}
			else {
				log.error("invalid conditional system name in Logix getStateVariableList - "+
															_conditionalSystemNames.get(i));

			}
		}
	}       // getStateVariableList
	*/
    /**
     * Deactivate the Logix. This method disconnects the Logix from
     *    all input objects and stops it from being triggered to calculate.
     * <P>
     * A Logix must be deactivated before it's Conditionals are
	 *   changed.
     */
    public void deActivateLogix() {
		if (_isActivated) {
			// Logix is active, deactivate it and all listeners
			_isActivated = false;
			// remove listeners if there are any
            for (int i=_listeners.size()-1; i>=0; i--) {
                removeListener(_listeners.get(i));
            }
		}
	}

	/**
	 * Creates a listener of the required type and starts it
	 */
    private void startListener(JmriSimplePropertyListener listener) {
        String msg = "(unknown type number "+listener.getType()+")";
		switch (listener.getType()) {
			case LISTENER_TYPE_SENSOR:
				Sensor s = InstanceManager.sensorManagerInstance().
										provideSensor(listener.getDevName());
				if (s==null) {
					msg = "sensor";
					break;
  				}
				s.addPropertyChangeListener (listener);
				return;
			case LISTENER_TYPE_TURNOUT:
				Turnout t = InstanceManager.turnoutManagerInstance().
										provideTurnout(listener.getDevName());
				if (t==null) {
					msg = "turnout";
					break;
				}
				t.addPropertyChangeListener (listener);
				return;
			case LISTENER_TYPE_LIGHT:
				Light lgt = InstanceManager.lightManagerInstance().
										getLight(listener.getDevName());
				if (lgt==null) {
					msg = "light";
					break;
				}
				lgt.addPropertyChangeListener (listener);
				return;
			case LISTENER_TYPE_CONDITIONAL:
				Conditional c = InstanceManager.conditionalManagerInstance().
										getConditional(listener.getDevName());
				if (c==null) {
					msg = "conditional";
					break;
				}
				c.addPropertyChangeListener (listener);
				return;
			case LISTENER_TYPE_SIGNAL:
				SignalHead h = InstanceManager.signalHeadManagerInstance().
										getSignalHead(listener.getDevName());
				if (h==null) {
					msg = "signal head";
					break;
				}
				h.addPropertyChangeListener (listener);
				return;
			case LISTENER_TYPE_MEMORY:
				Memory m = InstanceManager.memoryManagerInstance().
										provideMemory(listener.getDevName());
				if (m==null) {
					msg= "memory";
					break;
				}
				m.addPropertyChangeListener (listener);
				return;
            case LISTENER_TYPE_FASTCLOCK:
                Timebase tb = InstanceManager.timebaseInstance();
				tb.addMinuteChangeListener (listener);
				return;
		}
        log.error("Bad name for " +msg+" \""+listener.getDevName()+
                        "\" when setting up Logix listener");
	}
	
	/**
	 * Removes a listener of the required type
	 */
	private void removeListener(JmriSimplePropertyListener listener) {
        String msg = null;
        try {
            switch (listener.getType()) {
                case LISTENER_TYPE_SENSOR:
                    Sensor s = InstanceManager.sensorManagerInstance().
                                            provideSensor(listener.getDevName());
                    if (s==null) {
                        msg = "sensor";
                        break;
                    }
                    // remove listener for this Sensor
                    s.removePropertyChangeListener(listener);
                    return;
                case LISTENER_TYPE_TURNOUT:
                    Turnout t = InstanceManager.turnoutManagerInstance().
                                            provideTurnout(listener.getDevName());
                    if (t==null) {
                        msg = "turnout";
                        break;
                    }
                    // remove listener for this Turnout
                    t.removePropertyChangeListener(listener);
                    return;
                case LISTENER_TYPE_LIGHT:
                    Light lgt = InstanceManager.lightManagerInstance().
                                            getLight(listener.getDevName());
                    if (lgt==null) {
                        msg = "light";
                        break;
                    }
                    // remove listener for this Light
                    lgt.removePropertyChangeListener(listener);
                    return;
                case LISTENER_TYPE_CONDITIONAL:
                    Conditional c = InstanceManager.conditionalManagerInstance().
                                            getConditional(listener.getDevName());
                    if (c==null) {
                        msg = "conditional";
                        break;
                    }
                    // remove listener for this Conditional
                    c.removePropertyChangeListener(listener);
                    return;
                case LISTENER_TYPE_SIGNAL:
                    SignalHead h = InstanceManager.signalHeadManagerInstance().
                                            getSignalHead(listener.getDevName());
                    if (h==null) {
                        msg = "signal head";
                        break;
                    }
                    // remove listener for this Signal Head
                    h.removePropertyChangeListener(listener);
                    return;
                case LISTENER_TYPE_MEMORY:
                    Memory m = InstanceManager.memoryManagerInstance().
                                            provideMemory(listener.getDevName());
                    if (m==null) {
                        msg= "memory";
                        break;
                    }
                    // remove listener for this Memory
                    m.removePropertyChangeListener(listener);
                    return;
                case LISTENER_TYPE_FASTCLOCK:
                    Timebase tb = InstanceManager.timebaseInstance();
                    tb.removeMinuteChangeListener (listener);
                    return;
            }
        } catch (Throwable t) {
            log.error("Bad name for listener on \""+listener.getDevName()+"\": "+t);
        }
        log.error("Bad name for "+msg+" listener on \""+listener.getDevName()+
                        "\"when removing");
	}
	
	/** 
	 * Assembles a list of state variables that both trigger the Logix, and are
	 *   changed by it.  Returns true if any such variables were found.  Returns false
	 *   otherwise.
     * Can be called when Logix is enabled.
	 *
	public boolean checkLoopCondition() {
        loopGremlins = new ArrayList<String[]>();
		if (!_isActivated) {
			// Prepare a list of all variables used in conditionals
            java.util.HashSet <ConditionalVariable> variableList = new java.util.HashSet<ConditionalVariable>();
            ConditionalManager cm = InstanceManager.conditionalManagerInstance();
            for (int i=0; i<_conditionalSystemNames.size(); i++) {
                Conditional c = null;
                c = cm.getBySystemName(_conditionalSystemNames.get(i));
                if (c!=null) {
                    // Not necesary to modify methods, equals and hashcode. Redundacy checked in addGremlin
                    variableList.addAll(c.getCopyOfStateVariables());
                }
            }
            java.util.HashSet <ConditionalVariable> variableList = new java.util.HashSet<ConditionalVariable>();
            ConditionalVariable v = null;
				// check conditional action items
            Conditional c = null;
            for (int i=0; i<_conditionalSystemNames.size(); i++) {
                // get next conditional
                c = cm.getBySystemName(_conditionalSystemNames.get(i));
                if (c!=null) {
                    ArrayList <ConditionalAction> actionList = c.getCopyOfActions();
                    for (int j = 0; j < actionList.size(); j++) {
                        ConditionalAction action = actionList.get(j);
                        String sName = "";
                        String uName = "";
                        switch (action.getType()) {
                            case Conditional.ACTION_NONE:
                                break;
                            case Conditional.ACTION_SET_TURNOUT:
                            case Conditional.ACTION_DELAYED_TURNOUT:
                            case Conditional.ACTION_RESET_DELAYED_TURNOUT:
                            case Conditional.ACTION_CANCEL_TURNOUT_TIMERS:
                                Turnout t = InstanceManager.turnoutManagerInstance().
                                            provideTurnout(action.getDeviceName());
                                if (t!=null) {
                                    sName = t.getSystemName();
                                    uName = t.getUserName();
                                    // check for action on the same turnout
                                    Iterator <ConditionalVariable>it= variableList.iterator();
                                    while(it.hasNext()) {
                                        v = it.next();
                                        if (v.getType() == Conditional.TYPE_TURNOUT_CLOSED || 
                                            v.getType() == Conditional.TYPE_TURNOUT_THROWN) {
                                            if ( (v.getName().equals(sName)) ||
                                                    (v.getName().equals(uName)) ) {
                                                // possible conflict found
                                                addGremlin("Turnout", sName, uName);
                                            }
                                        }
                                    }
                                }
                                break;
                            case Conditional.ACTION_SET_SIGNAL_APPEARANCE:
                            case Conditional.ACTION_SET_SIGNAL_HELD:
                            case Conditional.ACTION_CLEAR_SIGNAL_HELD:
                            case Conditional.ACTION_SET_SIGNAL_DARK:
                            case Conditional.ACTION_SET_SIGNAL_LIT:
                                SignalHead h = InstanceManager.signalHeadManagerInstance().
                                                getSignalHead(action.getDeviceName());
                                if (h!=null) {
                                    sName = h.getSystemName();
                                    uName = h.getUserName();
                                    // check for action on the same signal head
                                    Iterator <ConditionalVariable>it= variableList.iterator();
                                    while(it.hasNext()) {
                                        v = it.next();
                                        if (v.getType() >= Conditional.TYPE_SIGNAL_HEAD_RED || 
                                            v.getType() <= Conditional.TYPE_SIGNAL_HEAD_HELD) {
                                            if ( (v.getName().equals(sName)) ||
                                                    (v.getName().equals(uName)) ) {
                                                // possible conflict found
                                                addGremlin("SignalHead", sName, uName);
                                            }
                                        }
                                    }
                                }
                                break;
                            case Conditional.ACTION_SET_SENSOR:
                            case Conditional.ACTION_DELAYED_SENSOR:
                            case Conditional.ACTION_RESET_DELAYED_SENSOR:
                            case Conditional.ACTION_CANCEL_SENSOR_TIMERS:
                                Sensor s = InstanceManager.sensorManagerInstance().
                                            provideSensor(action.getDeviceName());
                                if (s!=null) {
                                    sName = s.getSystemName();
                                    uName = s.getUserName();
                                    // check for action on the same sensor
                                    Iterator <ConditionalVariable>it= variableList.iterator();
                                    while(it.hasNext()) {
                                        v = it.next();
                                        if (v.getType() == Conditional.TYPE_SENSOR_ACTIVE || 
                                            v.getType() == Conditional.TYPE_SENSOR_INACTIVE) {

                                            if ( (v.getName().equals(sName)) ||
                                                    (v.getName().equals(uName)) ) {
                                                // possible conflict found
                                                addGremlin("Sensor",sName, uName);
                                            }
                                        }
                                    }
                                }
                                break;
                            case Conditional.ACTION_SET_LIGHT:
                            case Conditional.ACTION_SET_LIGHT_TRANSITION_TIME:
                            case Conditional.ACTION_SET_LIGHT_INTENSITY:
                                Light lgt = InstanceManager.lightManagerInstance().
                                                getLight(action.getDeviceName());
                                if (lgt!=null) {
                                    sName = lgt.getSystemName();
                                    uName = lgt.getUserName();
                                    // check for listener on the same light
                                    Iterator <ConditionalVariable>it= variableList.iterator();
                                    while(it.hasNext()) {
                                        v = it.next();
                                        if (v.getType() == Conditional.TYPE_LIGHT_ON || 
                                            v.getType() == Conditional.TYPE_LIGHT_OFF) {
                                            if ( (v.getName().equals(sName)) ||
                                                    (v.getName().equals(uName)) ) {
                                                // possible conflict found
                                                addGremlin("Light", sName, uName);
                                            }
                                        }
                                    }
                                }
                                break;
                            case Conditional.ACTION_SET_MEMORY:
                            case Conditional.ACTION_COPY_MEMORY:
                                Memory m = InstanceManager.memoryManagerInstance().
                                            provideMemory(action.getDeviceName());
                                if (m!=null) {
                                    sName = m.getSystemName();
                                    uName = m.getUserName();
                                    // check for variable on the same memory
                                    Iterator <ConditionalVariable>it= variableList.iterator();
                                    while(it.hasNext()) {
                                        v = it.next();
                                        if (v.getType() == Conditional.TYPE_MEMORY_EQUALS) {
                                            if ( (v.getName().equals(sName)) ||
                                                    (v.getName().equals(uName)) ) {
                                                // possible conflict found
                                                addGremlin("Memory", sName, uName);
                                            }
                                        }
                                    }
                                }
                                break;
                            case Conditional.ACTION_SET_FAST_CLOCK_TIME:
                            case Conditional.ACTION_START_FAST_CLOCK:
                            case Conditional.ACTION_STOP_FAST_CLOCK:
                                Iterator <ConditionalVariable>it= variableList.iterator();
                                while(it.hasNext()) {
                                    v = it.next();
                                    if (v.getType() == Conditional.TYPE_FAST_CLOCK_RANGE) {
                                            addGremlin("FastClock", null, v.getName());
                                    }
                                }
                                break;
                            default:
                        }							
                    }
                }
            }
        }
        return (loopGremlins.size()>0);
	}
    
	private void addGremlin(String type, String sName, String uName) {
        // check for redundancy
        String names = uName+ (sName == null ? "" : " ("+sName+")");
        for (int i=0; i<loopGremlins.size(); i++)
        {
            String[] str = loopGremlins.get(i);
            if (str[0].equals(type) && str[1].equals(names)) {
                return;
            }
        }
        String[] item = new String[2];
		item[0] = type;
		item[1] = names;
        loopGremlins.add(item);
	}
	
	ArrayList <String[]> loopGremlins = null;
	 
	/** 
	 * Returns a string listing state variables that might result in a loop.
	 *    Returns an empty string if there are none, probably because 
	 *    "checkLoopCondition" was not invoked before the call, or returned false.
	 *
	public ArrayList <String[]> getLoopGremlins() {return(loopGremlins);}
    */
    /**
     * Not needed for Logixs - included to complete implementation of the NamedBean interface.
     */
    public int getState() {
        log.warn("Unexpected call to getState in DefaultLogix.");
        return UNKNOWN;
    }
    
    /**
     * Not needed for Logixs - included to complete implementation of the NamedBean interface.
     */
    public void setState(int state) {
        log.warn("Unexpected call to setState in DefaultLogix.");
        return;
    }
	
    static final org.apache.log4j.Logger log = org.apache.log4j.Logger.getLogger(DefaultLogix.class.getName());
}

/* @(#)DefaultLogix.java */
