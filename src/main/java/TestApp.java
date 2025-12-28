public class TestApp {
  public static void main(String[] args) {
    System.out.println("Test app running...");
    try {
      Thread.sleep(3000);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      System.err.println("Test app interrupted: " + e.getMessage());
    }
    System.out.println("Test app finished");
  }
}
