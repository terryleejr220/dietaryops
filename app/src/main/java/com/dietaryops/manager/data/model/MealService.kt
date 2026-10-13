package com.dietaryops.manager.data.model

import java.time.LocalTime

enum class MealPeriod(val displayName: String) {
    BREAKFAST("Breakfast (6:00 AM - 10:30 AM)"),
    LUNCH("Lunch (10:30 AM - 3:30 PM)"),
    DINNER("Dinner (3:30 PM - 8:00 PM)");

    companion object {
        fun current(time: LocalTime = LocalTime.now()): MealPeriod {
            return when {
                time.isBefore(LocalTime.of(10, 30)) -> BREAKFAST
                time.isBefore(LocalTime.of(15, 30)) -> LUNCH
                else -> DINNER
            }
        }
    }
}

data class MealService(
    val period: MealPeriod,
    val mainEntree: String,
    val sides: List<String>,
    val dessert: String,
    val portionInstructions: String,
    val mechanicalSoftMod: String,
    val pureedMod: String,
    val startTime: String = "11:30 AM",
    val cartDepartureTime: String = "12:00 PM"
) {
    companion object {
        fun defaultSampleServices(): List<MealService> {
            return listOf(
                MealService(
                    period = MealPeriod.BREAKFAST,
                    mainEntree = "Scrambled Eggs & Sausage Patties",
                    sides = listOf("Oatmeal / Cream of Wheat", "Hash Brown Potatoes", "Biscuit & Gravy"),
                    dessert = "Chilled Fruit Cup / Orange Juice",
                    portionInstructions = "#8 Scoop Eggs (4 oz) • 2 Patties • 1 Biscuit",
                    mechanicalSoftMod = "Soft Scrambled Eggs & Ground Sausage",
                    pureedMod = "Pureed Eggs & Pureed Sausage",
                    startTime = "7:00 AM",
                    cartDepartureTime = "7:30 AM"
                ),
                MealService(
                    period = MealPeriod.LUNCH,
                    mainEntree = "Roast Pork Loin with Apple Glaze",
                    sides = listOf("Mashed Potatoes & Pork Gravy", "Steamed Green Beans"),
                    dessert = "Yellow Cake with Vanilla Frosting",
                    portionInstructions = "3 oz Sliced Pork • #8 Scoop Potatoes • #8 Scoop Beans",
                    mechanicalSoftMod = "Ground Pork Loin & Soft Potatoes",
                    pureedMod = "Pureed Pork Loin, Pureed Potatoes & Pureed Beans",
                    startTime = "11:30 AM",
                    cartDepartureTime = "12:00 PM"
                ),
                MealService(
                    period = MealPeriod.DINNER,
                    mainEntree = "Baked Chicken Breast Tenderloin",
                    sides = listOf("Rice Pilaf", "Glazed Carrots"),
                    dessert = "Chilled Peaches / Chocolate Pudding",
                    portionInstructions = "2 Chicken Tenders • #8 Scoop Rice • #8 Scoop Carrots",
                    mechanicalSoftMod = "Diced Chicken & Soft Carrots",
                    pureedMod = "Pureed Chicken, Pureed Rice & Pureed Carrots",
                    startTime = "4:30 PM",
                    cartDepartureTime = "5:00 PM"
                )
            )
        }
    }
}
