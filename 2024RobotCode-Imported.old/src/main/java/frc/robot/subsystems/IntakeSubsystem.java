
// Copyright (c) FIRST and other WPILib contributors.
// Open Source Software; you can modify and/or share it under the terms of
// the WPILib BSD license file in the root directory of this project.
package frc.robot.subsystems;


import com.revrobotics.CANSparkMax;

import edu.wpi.first.wpilibj2.command.SubsystemBase;
import frc.robot.classes.DebugLog;
import frc.robot.classes.TimeOfFlightSensor;
import edu.wpi.first.wpilibj2.command.Command;
import edu.wpi.first.wpilibj2.command.StartEndCommand;
import frc.robot.hardware.MotorController;
import frc.robot.hardware.MotorController.MotorConfig;
import edu.wpi.first.networktables.GenericEntry;
import edu.wpi.first.wpilibj.shuffleboard.Shuffleboard;
import edu.wpi.first.wpilibj.shuffleboard.ShuffleboardTab;
import edu.wpi.first.wpilibj.shuffleboard.BuiltInWidgets;
import java.util.Map;
import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;

public class IntakeSubsystem extends SubsystemBase implements AutoCloseable {
  public enum FlightStates{
    IDLE,
    CONE,
    CONE_SCORE
  }

  public static final double kConeIntakeSpeed = -0.75;
  public static final double kConeOuttakeSpeed = 0.75;
  public static final double kCubeIntakeSpeed = 0.55;
  public static final double kCubeOuttakeSpeed = -0.3;

  private final double mmConeActivationThreshold = 450.0; 
  private final double mmCubeActivationThreshold = 450.0; 
  private FlightStates tofState = FlightStates.IDLE;

  private TimeOfFlightSensor timeOfFlightSensor;

  private CANSparkMax motor;
  private CANSparkMax motor2;

  private static ShuffleboardTab matchTab = Shuffleboard.getTab("Match");
  private static GenericEntry intakeEntry = matchTab.add("Intake Speed", 0.0).getEntry();
  private static GenericEntry intakeMode = matchTab.add("Intake Mode", true).withWidget(BuiltInWidgets.kBooleanBox).withProperties(Map.of("colorWhenFalse", "Purple", "colorWhenTrue", "Yellow")).getEntry();
  private static GenericEntry scoreMode = matchTab.add("Has Cube", true).withWidget(BuiltInWidgets.kBooleanBox).withProperties(Map.of("colorWhenFalse", "Red", "colorWhenTrue", "Green")).getEntry();
  private static GenericEntry currentState = matchTab.add("ToF State", FlightStates.IDLE.toString()).getEntry();
  private static GenericEntry sensor0Up = matchTab.add("Cone/Cube ToF sensor up: ", true).getEntry();
  private static GenericEntry cubeDistanceEntry = matchTab.add("Cube distance mm ", true).getEntry(); 

  private DebugLog<Double> coneDistLog = new DebugLog<Double>(0.0, "Cone/Cube Distance", this);
  private DebugLog<Boolean> hasConeLog = new DebugLog<Boolean>(false, "Has Cone/Cube", this);

  private boolean isConeMode;
  private boolean cubeOverride;

  /** Creates a new IntakeSubsystem. */
  public IntakeSubsystem() {
    isConeMode = true;
    cubeOverride = false;

    motor = MotorController.constructMotor(MotorConfig.IntakeMotor1);
    motor2 = MotorController.constructMotor(MotorConfig.IntakeMotor2);
  }

  public IntakeSubsystem(TimeOfFlightSensor timeOfFlightSensor) {
    this();
    this.timeOfFlightSensor = timeOfFlightSensor;
  }
  
  private void spinWheels(double velocity) {
    motor.set(velocity);
    motor2.set(-velocity);
    intakeEntry.setDouble(velocity);
  }

  public void push() {
    spinWheels(isConeMode ? kConeOuttakeSpeed : kCubeOuttakeSpeed);
  }

  public void pull() {
    spinWheels(isConeMode ? kConeIntakeSpeed : kCubeIntakeSpeed);
  }

    public void setConeMode() {
      isConeMode = true;
  }

  public void setCubeMode() {
      isConeMode = false;
  }

  public void toggleConeMode() {
    isConeMode = !isConeMode;
  }

  public void setCubeOverride(boolean cubeOverride){
    this.cubeOverride = cubeOverride;
  }

  public boolean hasCube() {
    int cubeDistance = timeOfFlightSensor.getDistance0();
    boolean cubeSensorUp = cubeDistance != -1;
    return cubeOverride || (cubeDistance <= mmCubeActivationThreshold && cubeSensorUp);
  }
  
  public double getSpeed(){
    return motor.get();
  }

  public void stop() {
    spinWheels(0);
  }

  public Command pullTimed(double seconds, boolean coneMode){
    return new StartEndCommand(this::pull, this::stop, this).withTimeout(seconds).beforeStarting(coneMode ? this::setConeMode : this::setCubeMode);
  }
  
  public Command pushTimed(double seconds, boolean coneMode){
    return new StartEndCommand(this::push, this::stop, this).withTimeout(seconds).beforeStarting(coneMode ? this::setConeMode : this::setCubeMode);
  }

  public void hold() {
    spinWheels(isConeMode ? kConeIntakeSpeed : kCubeIntakeSpeed);

  }

  @Override
  public void close() throws Exception {
    // This method will close all device handles used by this object and release any other dynamic memory.
    // Mostly for JUnit tests
    motor.close();
    motor2.close();
  }

  public void changeFlightState() {
    // Check distances
    int coneDistance = timeOfFlightSensor.getDistance0();

    boolean coneSensorUp = coneDistance != -1;


    // Log if sensors are activated
    hasConeLog.log(coneDistance <= mmConeActivationThreshold && coneSensorUp);

    // Change state (only if sensors are online)
    switch(tofState) {
      case IDLE:
        if (coneSensorUp && coneDistance <= mmConeActivationThreshold) {
          tofState = FlightStates.CONE;
        }
        break;
      case CONE:
        if (coneSensorUp && coneDistance > mmConeActivationThreshold) {
          tofState = FlightStates.IDLE;
        }
        break;
      case CONE_SCORE:
        if (coneSensorUp && coneDistance > mmConeActivationThreshold) {
          tofState = FlightStates.IDLE;
        }
        break;
    }
  }

  public FlightStates getFlightState() {
    // Check if state has changed before returning it
    changeFlightState();
    currentState.setString(tofState.toString());
    return tofState;
  }

  @Override
  public void periodic() {
    // This method will be called once per scheduler run
    SmartDashboard.putData(this);
    intakeMode.setBoolean(isConeMode);
    scoreMode.setBoolean(hasCube());

    int coneDistance = timeOfFlightSensor.getDistance0();
    int cubeDistance = timeOfFlightSensor.getDistance0();
    cubeDistanceEntry.setInteger(cubeDistance);

    // Log distance values
    coneDistLog.log((double)coneDistance);

    // Check if sensors are online
    boolean coneSensorUp = coneDistance != -1;

    // Log whether sensors are online
    sensor0Up.setBoolean(coneSensorUp);
  }

  @Override
  public void simulationPeriodic() {
    // This method will be called once per scheduler run during simulation
  }
}
