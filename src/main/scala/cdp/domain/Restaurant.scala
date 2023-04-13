package cdp.domain

import java.time.Instant

case class Restaurant(id: Int, name: String, city: String, restaurantType: RestaurantType)

sealed trait RestaurantType
object RestaurantType {
  case object FastFood extends RestaurantType
  case object Asian extends RestaurantType
  case object Italian extends RestaurantType
  case object Homemade extends RestaurantType
  case object Indian extends RestaurantType
}

case class Meal(id: Int, restaurant: Restaurant, price: Double, mealType: MealType)

case class Order(id: Int, event_time: Instant, member: Member, restaurant: Restaurant, total_cost: Double, meals: List[Meal])

case class Member(id: Int, name: String, age: String, sex: Boolean, email: String, city: String, monthlyBudget: Double)

case class MemberOrders(member: Member, orderCount: Int, mealCount: Int, monthlyBudget: Double, totalExpense: Double, balance: Double, commission: Double)

sealed trait MealType
object MealType {
  case object Vegan extends MealType
  case object Cheese extends MealType
  case object Beef extends MealType
  case object Chicken extends MealType
}
