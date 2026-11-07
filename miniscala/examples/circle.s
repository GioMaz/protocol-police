{
  class Circle(x, y, r) {
    val center = (x, y);
    var radius = r;
    def getCircumference() = r * 2 * 3.14159f
  };
  {
    val x = new Circle(17, 87, 117);
    x.getCircumference()
  }
}
