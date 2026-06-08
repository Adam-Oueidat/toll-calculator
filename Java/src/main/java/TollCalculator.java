
import de.focus_shift.jollyday.core.HolidayManager;
import de.focus_shift.jollyday.core.ManagerParameters;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.*;

public class TollCalculator {

  /**
   * Calculate the total toll fee for one day
   *
   * @param vehicle - the vehicle
   * @param dates   - date and time of all passes on one day
   * @return - the total toll fee for that day
   */
  private static final int CHARGE_INTERVAL_MINUTES = 60;
  private static final int MAX_DAILY_FEE = 60;

  public int getTollFee(Vehicle vehicle, Date... dates) {
    Date intervalStart = dates[0];
    int totalFee = 0;
    int windowFee = 0;

    for (Date date : dates) {
      int currentFee = getTollFee(date, vehicle);

      if (minutesBetween(intervalStart, date) <= CHARGE_INTERVAL_MINUTES) {
        windowFee = Math.max(windowFee, currentFee);
      } else {
        totalFee += windowFee;
        intervalStart = date;
        windowFee = currentFee;
      }
    }

    totalFee += windowFee;
    return Math.min(totalFee, MAX_DAILY_FEE);
  }

  private long minutesBetween(Date from, Date to) {
    return TimeUnit.MILLISECONDS.toMinutes(to.getTime() - from.getTime());
  }

  private boolean isTollFreeVehicle(Vehicle vehicle) {
    if(vehicle == null) return false;
    String vehicleType = vehicle.getType();
    return vehicleType.equals(TollFreeVehicles.MOTORBIKE.getType()) ||
           vehicleType.equals(TollFreeVehicles.TRACTOR.getType()) ||
           vehicleType.equals(TollFreeVehicles.EMERGENCY.getType()) ||
           vehicleType.equals(TollFreeVehicles.DIPLOMAT.getType()) ||
           vehicleType.equals(TollFreeVehicles.FOREIGN.getType()) ||
           vehicleType.equals(TollFreeVehicles.MILITARY.getType());
  }

  private record FeeSlot(int fromHour, int fromMinute, int toHour, int toMinute, int fee) {
    boolean contains(int hour, int minute) {
      int time = hour * 60 + minute;
      return time >= fromHour * 60 + fromMinute && time <= toHour * 60 + toMinute;
    }
  }

  private static final List<FeeSlot> SCHEDULE = List.of(
    new FeeSlot( 6,  0,  6, 29,  8),
    new FeeSlot( 6, 30,  6, 59, 13),
    new FeeSlot( 7,  0,  7, 59, 18),
    new FeeSlot( 8,  0,  8, 29, 13),
    new FeeSlot( 8, 30, 14, 59,  8),
    new FeeSlot(15,  0, 15, 29, 13),
    new FeeSlot(15, 30, 16, 59, 18),
    new FeeSlot(17,  0, 17, 59, 13),
    new FeeSlot(18,  0, 18, 29,  8)
  );

  public int getTollFee(final Date date, Vehicle vehicle) {
    if (isTollFreeDate(date) || isTollFreeVehicle(vehicle)) return 0;
    Calendar calendar = GregorianCalendar.getInstance();
    calendar.setTime(date);
    int hour = calendar.get(Calendar.HOUR_OF_DAY);
    int minute = calendar.get(Calendar.MINUTE);

    return SCHEDULE.stream()
        .filter(slot -> slot.contains(hour, minute))
        .mapToInt(FeeSlot::fee)
        .findFirst()
        .orElse(0);
  }


  /**
   * Utilize jollyday to ensure we find the correct swedish holidays. Can be adjusted to be configurable perhaps but the assumption is that it is a swedish city.
   */
  private static final HolidayManager HOLIDAYS =
      HolidayManager.getInstance(ManagerParameters.create("se"));

  private Boolean isTollFreeDate(Date date) {
    LocalDate localDate = date.toInstant()
        .atZone(ZoneId.systemDefault())
        .toLocalDate();

    if (localDate.getDayOfWeek().getValue() >= 6) return true;

    return HOLIDAYS.isHoliday(localDate);
  }

  private enum TollFreeVehicles {
    MOTORBIKE("Motorbike"),
    TRACTOR("Tractor"),
    EMERGENCY("Emergency"),
    DIPLOMAT("Diplomat"),
    FOREIGN("Foreign"),
    MILITARY("Military");
    private final String type;

    TollFreeVehicles(String type) {
      this.type = type;
    }

    public String getType() {
      return type;
    }
  }
}
