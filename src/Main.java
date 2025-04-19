import java.util.*;
import java.util.concurrent.*;

// Enum for vehicle types
enum VehicleType { SMALL, MEDIUM, LARGE }

enum CustomerType { REGULAR, VIP }

// Abstract Vehicle class
abstract class Vehicle {
    String vehicleId;
    VehicleType type;
    long entryTime;
    CustomerType customerType;

    public Vehicle(String vehicleId, VehicleType type, long entryTime, CustomerType customerType) {
        this.vehicleId = vehicleId;
        this.type = type;
        this.entryTime = entryTime;
        this.customerType = customerType;
    }

    public boolean isPriority() {
        return customerType == CustomerType.VIP;
    }
}

class SmallVehicle extends Vehicle {
    public SmallVehicle(String vehicleId, long entryTime, CustomerType customerType) {
        super(vehicleId, VehicleType.SMALL, entryTime, customerType);
    }
}

class MediumVehicle extends Vehicle {
    public MediumVehicle(String vehicleId, long entryTime, CustomerType customerType) {
        super(vehicleId, VehicleType.MEDIUM, entryTime, customerType);
    }
}

class LargeVehicle extends Vehicle {
    public LargeVehicle(String vehicleId, long entryTime, CustomerType customerType) {
        super(vehicleId, VehicleType.LARGE, entryTime, customerType);
    }
}

class ParkingSlot {
    int slotId;
    boolean isOccupied = false;
    VehicleType type;

    public ParkingSlot(int slotId, VehicleType type) {
        this.slotId = slotId;
        this.type = type;
    }
}

class ParkingLevel {
    int levelId;
    List<ParkingSlot> slots = new ArrayList<>();

    public ParkingLevel(int levelId, int small, int medium, int large) {
        this.levelId = levelId;
        int id = 1;
        for (int i = 0; i < small; i++) slots.add(new ParkingSlot(id++, VehicleType.SMALL));
        for (int i = 0; i < medium; i++) slots.add(new ParkingSlot(id++, VehicleType.MEDIUM));
        for (int i = 0; i < large; i++) slots.add(new ParkingSlot(id++, VehicleType.LARGE));
    }

    public synchronized ParkingSlot assignSlot(Vehicle vehicle) {
        for (ParkingSlot slot : slots) {
            if (!slot.isOccupied && slot.type == vehicle.type) {
                slot.isOccupied = true;
                return slot;
            }
        }
        return null;
    }
}

class ParkingLot {
    List<ParkingLevel> levels = new ArrayList<>();
    Map<String, Long> lastEntryTime = new ConcurrentHashMap<>();
    long reEntryRestrictionMillis = 60 * 60 * 1000; // 1 hour

    public ParkingLot() {
        levels.add(new ParkingLevel(1, 10, 10, 5));
        levels.add(new ParkingLevel(2, 10, 10, 5));
    }

    public synchronized boolean canReEnter(Vehicle v) {
        if (!lastEntryTime.containsKey(v.vehicleId)) return true;
        long lastTime = lastEntryTime.get(v.vehicleId);
        return System.currentTimeMillis() - lastTime > reEntryRestrictionMillis;
    }

    public synchronized boolean allocateSlot(Vehicle vehicle) {
        if (!canReEnter(vehicle)) {
            System.out.println(vehicle.vehicleId + " denied due to re-entry restriction.");
            return false;
        }
        for (ParkingLevel level : levels) {
            ParkingSlot slot = level.assignSlot(vehicle);
            if (slot != null) {
                lastEntryTime.put(vehicle.vehicleId, System.currentTimeMillis());
                System.out.println(vehicle.vehicleId + " parked at level " + level.levelId + ", slot " + slot.slotId);
                return true;
            }
        }
        System.out.println("No slot available for " + vehicle.vehicleId);
        return false;
    }
}

class ParkingService implements Runnable {
    BlockingQueue<Vehicle> requestQueue;
    ParkingLot lot;

    public ParkingService(BlockingQueue<Vehicle> requestQueue, ParkingLot lot) {
        this.requestQueue = requestQueue;
        this.lot = lot;
    }

    @Override
    public void run() {
        while (true) {
            try {
                Vehicle vehicle = requestQueue.take();
                lot.allocateSlot(vehicle);
            } catch (InterruptedException e) {
                break;
            }
        }
    }
}

class ParkingAgent implements Runnable {
    BlockingQueue<Vehicle> requestQueue;
    Vehicle vehicle;

    public ParkingAgent(BlockingQueue<Vehicle> queue, Vehicle vehicle) {
        this.requestQueue = queue;
        this.vehicle = vehicle;
    }

    @Override
    public void run() {
        try {
            requestQueue.put(vehicle);
            System.out.println(vehicle.vehicleId + " requested parking.");
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}

public class Main {
    public static void main(String[] args) throws InterruptedException {
        BlockingQueue<Vehicle> requestQueue = new PriorityBlockingQueue<>(100, Comparator.comparing(Vehicle::isPriority).reversed());
        ParkingLot lot = new ParkingLot();
        Thread serviceThread = new Thread(new ParkingService(requestQueue, lot));
        serviceThread.start();

        List<Thread> agents = List.of(
                new Thread(new ParkingAgent(requestQueue, new SmallVehicle("V1", System.currentTimeMillis(), CustomerType.REGULAR))),
                new Thread(new ParkingAgent(requestQueue, new LargeVehicle("V2", System.currentTimeMillis(), CustomerType.VIP))),
                new Thread(new ParkingAgent(requestQueue, new MediumVehicle("V3", System.currentTimeMillis(), CustomerType.REGULAR))),
                new Thread(new ParkingAgent(requestQueue, new SmallVehicle("V1", System.currentTimeMillis(), CustomerType.REGULAR)))
        );

        for (Thread agent : agents) {
            agent.start();
        }

        for (Thread agent : agents) {
            agent.join();
        }

        Thread.sleep(2000);
        serviceThread.interrupt();
    }
}
