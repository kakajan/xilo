"use server";

import { revalidatePath, revalidateTag } from "next/cache";

export async function revalidatePost(slug: string, username: string) {
  revalidatePath(`/${username}/${slug}`);
  revalidateTag("posts");
}

export async function revalidateHomepage() {
  revalidatePath("/");
  revalidateTag("posts");
}

export async function revalidateAuthor(username: string) {
  revalidatePath(`/${username}`);
  revalidateTag("user-posts");
}

export async function revalidateCategory(slug: string) {
  revalidatePath(`/category/${slug}`);
  revalidateTag("category-posts");
}
