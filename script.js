const btn = document.getElementById('btn');
btn.addEventListener('click', () => {
  console.log("buttton is clicked..");
  shortenUrl();
})
function shortenUrl() {
  const longUrl = document.getElementById("longurl").value;
  console.log("url", longUrl);

  fetch("http://localhost:80/url-shortener/shorten", {
    method: "POST",
    headers: {
      "Content-Type": "application/json"
    },
    body: JSON.stringify({ url: longUrl })
  })
    .then(response => response.text())
    .then(shortUrl => {
      document.getElementById("result").innerHTML = `Shortened URL: <a href="${shortUrl}" target="_blank">${shortUrl}</a>`;
    })
    .catch(error => {
      console.error("Error:", error);
    });
}