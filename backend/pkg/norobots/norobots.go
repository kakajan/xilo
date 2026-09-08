package norobots

import "github.com/gofiber/fiber/v2"

const HeaderValue = "noindex, nofollow, noarchive"

// RobotsBody tells every crawler to stay off the API host.
const RobotsBody = "User-agent: *\nDisallow: /\n"

func Middleware(c *fiber.Ctx) error {
	c.Set("X-Robots-Tag", HeaderValue)
	return c.Next()
}

func Robots(c *fiber.Ctx) error {
	c.Set(fiber.HeaderContentType, "text/plain; charset=utf-8")
	c.Set("Cache-Control", "public, max-age=3600")
	return c.SendString(RobotsBody)
}
